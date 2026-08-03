package com.kiwi.project.bpm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Nexus-backed 远程市场配置（{@code kiwi.bpm.remote-market.*}）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.bpm.remote-market")
public class BpmRemoteMarketProperties {

    private boolean enabled;
    private String kiwiVersion = "1.0.0-SNAPSHOT";
    private int cacheTtlSeconds = 300;
    private List<Source> sources = new ArrayList<>();

    @Data
    public static class Source {
        private String id;
        private String name;
        private String baseUrl;
        private String indexPath = "market/index.json";
        private String username;
        private String password;
    }
}
