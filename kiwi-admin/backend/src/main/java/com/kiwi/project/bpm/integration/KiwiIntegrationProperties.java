package com.kiwi.project.bpm.integration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cryoEMS 等与 Kiwi 的流程集成（共享密钥机机调用）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.integration")
public class KiwiIntegrationProperties {

    private final Machine machine = new Machine();

    @Data
    public static class Machine {
        /** cryoEMS → Kiwi 启动流程时校验请求头 {@code X-Kiwi-Integration-Secret} */
        private String secret = "";
    }
}
