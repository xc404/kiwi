package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.config.BpmRemoteMarketProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 从远程市场源拉取 HTTP 资源（支持 Basic 认证）。
 */
@Component
@RequiredArgsConstructor
public class BpmRemoteMarketHttpFetcher {

    private static final int ConnectTimeoutSec = 15;
    private static final int ReadTimeoutSec = 120;
    private static final int MaxBytes = 100 * 1024 * 1024;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(ConnectTimeoutSec))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String fetchText(String url, BpmRemoteMarketProperties.Source source) {
        return new String(fetchBytes(url, source), StandardCharsets.UTF_8);
    }

    public byte[] fetchBytes(String urlRaw, BpmRemoteMarketProperties.Source source) {
        URI uri = resolveUri(urlRaw, source);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(ReadTimeoutSec))
                .GET();
        applyAuth(builder, source);
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "远程市场请求失败 HTTP " + response.statusCode() + ": " + uri);
            }
            byte[] body = response.body();
            if (body.length > MaxBytes) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "远程资源过大: " + uri);
            }
            return body;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "远程市场 IO 失败: " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "远程市场请求被中断: " + uri, e);
        }
    }

    public String resolveUrl(String url, BpmRemoteMarketProperties.Source source) {
        return resolveUri(url, source).toString();
    }

    private URI resolveUri(String urlRaw, BpmRemoteMarketProperties.Source source) {
        if (StringUtils.isBlank(urlRaw)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "远程 URL 不能为空");
        }
        String trimmed = urlRaw.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return URI.create(trimmed);
        }
        String base = StringUtils.defaultString(source.getBaseUrl()).trim();
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        String path = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        return URI.create(base + path);
    }

    private void applyAuth(HttpRequest.Builder builder, BpmRemoteMarketProperties.Source source) {
        if (source == null || StringUtils.isAnyBlank(source.getUsername(), source.getPassword())) {
            return;
        }
        String token = source.getUsername() + ":" + source.getPassword();
        String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + encoded);
    }
}
