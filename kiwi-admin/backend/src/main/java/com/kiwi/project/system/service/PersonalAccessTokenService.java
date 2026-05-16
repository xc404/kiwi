package com.kiwi.project.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.kiwi.framework.security.PasswordService;
import com.kiwi.project.system.dao.SysPersonalAccessTokenDao;
import com.kiwi.project.system.entity.SysPersonalAccessToken;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PersonalAccessTokenService {

    public static final String SA_DEVICE_PREFIX = "pat-";
    public static final String PAT_PREFIX = "kip_";
    private static final Pattern PAT_FORMAT =
            Pattern.compile("^kip_([a-f0-9]{24})_([A-Za-z0-9_-]{32,})$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SysPersonalAccessTokenDao tokenDao;
    private final PasswordService passwordService;

    /** {@code kiwi.personal-access-token.max-per-user}；≤0 表示不限制。 */
    @Value("${kiwi.personal-access-token.max-per-user:30}")
    private int personalAccessTokenMaxPerUser;

    /** {@code kiwi.personal-access-token.session-timeout-seconds}：PAT 凭证有效期（秒）。 */
    @Value("${kiwi.personal-access-token.session-timeout-seconds:31536000}")
    private long patLifetimeSeconds;

    /** {@code kiwi.personal-access-token.exchange-session-timeout-seconds}：兑换所得 Sa-Token 会话有效期（秒）。 */
    @Value("${kiwi.personal-access-token.exchange-session-timeout-seconds:7200}")
    private long exchangeSessionTimeoutSeconds;

    public List<SysPersonalAccessToken> listMine(String userId) {
        Query q = Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "createdTime"));
        return tokenDao.findBy(q);
    }

    public CreateResult create(String userId, String name) {
        if (StringUtils.isNotBlank(name) && name.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "备注名称长度不能超过 64 字符");
        }
        int max = personalAccessTokenMaxPerUser;
        if (max > 0) {
            long n = tokenDao.countBy(Query.query(Criteria.where("userId").is(userId)));
            if (n >= max) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "长期访问令牌数量已达上限（" + max + "），请先删除不再使用的令牌");
            }
        }
        long patTtl = patLifetimeSeconds;
        if (patTtl <= 0) {
            patTtl = 60L * 60 * 24 * 365;
        }

        String id = new ObjectId().toHexString();
        String saDevice = SA_DEVICE_PREFIX + id;
        Date expiresAt = new Date(System.currentTimeMillis() + patTtl * 1000L);
        String plainPat = PAT_PREFIX + id + "_" + randomSecret();

        SysPersonalAccessToken doc = new SysPersonalAccessToken();
        doc.setId(id);
        doc.setUserId(userId);
        doc.setName(StringUtils.trimToNull(name));
        doc.setSaDevice(saDevice);
        doc.setTokenHash(hashPat(plainPat));
        doc.setTokenHint(maskToken(plainPat));
        doc.setExpiresAt(expiresAt);
        tokenDao.save(doc);

        return new CreateResult(id, plainPat, patTtl, expiresAt);
    }

    /**
     * 使用 PAT 兑换短期 Sa-Token（供机机调用业务 API）。
     */
    public ExchangeResult exchange(String rawPat) {
        String pat = extractPat(rawPat);
        Matcher m = PAT_FORMAT.matcher(pat);
        if (!m.matches()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的个人访问令牌");
        }
        String tokenId = m.group(1);
        SysPersonalAccessToken doc = tokenDao.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的个人访问令牌"));
        if (doc.getExpiresAt() != null && doc.getExpiresAt().before(new Date())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "个人访问令牌已过期");
        }
        String expectedHash = doc.getTokenHash();
        if (StringUtils.isBlank(expectedHash) || !constantTimeEquals(hashPat(pat), expectedHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的个人访问令牌");
        }

        long ttl = exchangeSessionTimeoutSeconds;
        if (ttl <= 0) {
            ttl = 7200;
        }
        SaLoginParameter param = SaLoginParameter.create()
                .setDevice(doc.getSaDevice())
                .setTimeout(ttl)
                .setIsWriteHeader(false);
        String saToken = StpUtil.createLoginSession(doc.getUserId(), param);
        Date saExpiresAt = new Date(System.currentTimeMillis() + ttl * 1000L);
        return new ExchangeResult(saToken, ttl, saExpiresAt);
    }

    public void revoke(String userId, String tokenId) {
        SysPersonalAccessToken doc = tokenDao.findById(tokenId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "令牌不存在"));
        if (!userId.equals(doc.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权操作该令牌");
        }
        try {
            StpUtil.logout(userId, doc.getSaDevice());
        } finally {
            tokenDao.deleteById(tokenId);
        }
    }

    private String hashPat(String pat) {
        return passwordService.encodePassword(pat);
    }

    private static String randomSecret() {
        byte[] buf = new byte[32];
        SECURE_RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String extractPat(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少个人访问令牌");
        }
        String t = raw.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (x.length != y.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 9) {
            return "****";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }

    @Data
    public static class CreateResult {
        private final String id;
        private final String token;
        private final long expiresInSeconds;
        private final Date expiresAt;
    }

    @Data
    public static class ExchangeResult {
        private final String token;
        private final long expiresInSeconds;
        private final Date expiresAt;
    }
}
