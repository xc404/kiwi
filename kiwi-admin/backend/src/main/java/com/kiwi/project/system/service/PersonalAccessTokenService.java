package com.kiwi.project.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.kiwi.project.bpm.integration.KiwiIntegrationProperties;
import com.kiwi.project.system.dao.SysPersonalAccessTokenDao;
import com.kiwi.project.system.entity.SysPersonalAccessToken;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonalAccessTokenService {

    public static final String SA_DEVICE_PREFIX = "pat-";

    private final SysPersonalAccessTokenDao tokenDao;
    private final KiwiIntegrationProperties integrationProperties;

    public List<SysPersonalAccessToken> listMine(String userId) {
        Query q = Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "createdTime"));
        return tokenDao.findBy(q);
    }

    public CreateResult create(String userId, String name) {
        if (StringUtils.isNotBlank(name) && name.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "备注名称长度不能超过 64 字符");
        }
        int max = integrationProperties.getPersonalAccessTokenMaxPerUser();
        if (max > 0) {
            long n = tokenDao.countBy(Query.query(Criteria.where("userId").is(userId)));
            if (n >= max) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "长期访问令牌数量已达上限（" + max + "），请先删除不再使用的令牌");
            }
        }
        long ttl = integrationProperties.getApiTokenTimeoutSeconds();
        if (ttl <= 0) {
            ttl = 60L * 60 * 24 * 365;
        }

        String id = new ObjectId().toHexString();
        String saDevice = SA_DEVICE_PREFIX + id;
        Date expiresAt = new Date(System.currentTimeMillis() + ttl * 1000L);

        SysPersonalAccessToken doc = new SysPersonalAccessToken();
        doc.setId(id);
        doc.setUserId(userId);
        doc.setName(StringUtils.trimToNull(name));
        doc.setSaDevice(saDevice);
        doc.setExpiresAt(expiresAt);
        tokenDao.save(doc);

        try {
            SaLoginParameter param = SaLoginParameter.create()
                    .setDevice(saDevice)
                    .setTimeout(ttl)
                    .setIsWriteHeader(false);
            String token = StpUtil.createLoginSession(userId, param);
            doc.setTokenHint(maskToken(token));
            tokenDao.save(doc);
            return new CreateResult(id, token, ttl, expiresAt);
        } catch (RuntimeException e) {
            tokenDao.deleteById(id);
            throw e;
        }
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
}
