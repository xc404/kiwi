package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.config.BpmRemoteMarketProperties;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class BpmRemoteMarketDownloadService {

    private final BpmRemoteMarketHttpFetcher httpFetcher;
    private final BpmRemoteMarketService marketService;

    public byte[] downloadVerified(BpmRemoteMarketItemDto item) {
        if (StringUtils.isBlank(item.getDownloadUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "downloadUrl 为空");
        }
        if (StringUtils.isBlank(item.getSha256())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sha256 为空");
        }
        BpmRemoteMarketProperties.Source source = marketService.requireSource(item.getSourceId());
        byte[] bytes = httpFetcher.fetchBytes(item.getDownloadUrl(), source);
        verifySha256(bytes, item.getSha256());
        return bytes;
    }

    private void verifySha256(byte[] bytes, String expectedHex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String actual = HexFormat.of().formatHex(digest.digest(bytes));
            if (!actual.equalsIgnoreCase(expectedHex.trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "SHA-256 校验失败：期望 " + expectedHex + "，实际 " + actual);
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "SHA-256 计算失败", e);
        }
    }
}
