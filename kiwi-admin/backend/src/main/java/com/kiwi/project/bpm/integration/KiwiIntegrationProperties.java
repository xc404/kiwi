package com.kiwi.project.bpm.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cryoEMS 等与 Kiwi 的流程集成（机机调用走 Sa-Token 个人长期访问令牌）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.integration")
public class KiwiIntegrationProperties {

    /**
     * 每条个人长期访问令牌在 Sa-Token 中的有效期（秒）。默认约 365 天；
     * 可通过环境变量 {@code KIWI_INTEGRATION_API_TOKEN_TIMEOUT} 覆盖。
     */
    private long apiTokenTimeoutSeconds = 60L * 60 * 24 * 365;

    /**
     * 单用户最多保留的令牌条数；达到上限需先删除。≤0 表示不限制（不推荐生产环境）。
     */
    private int personalAccessTokenMaxPerUser = 30;
}
