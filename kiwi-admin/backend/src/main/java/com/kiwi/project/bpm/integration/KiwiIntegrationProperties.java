package com.kiwi.project.bpm.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cryoEMS 等与 Kiwi 的流程集成（机机调用走 Sa-Token，用户在个人中心签发长期 Token）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.integration")
public class KiwiIntegrationProperties {

    /**
     * 个人中心签发的机机集成 Token 使用独立终端标识（见 {@link IntegrationDevice#TYPE}），其有效期（秒）。
     * 默认约 365 天；可通过环境变量 {@code KIWI_INTEGRATION_API_TOKEN_TIMEOUT} 覆盖。
     */
    private long apiTokenTimeoutSeconds = 60L * 60 * 24 * 365;
}
