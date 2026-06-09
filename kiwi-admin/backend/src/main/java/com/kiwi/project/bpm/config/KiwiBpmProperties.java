package com.kiwi.project.bpm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kiwi BPM 运行时配置（与 {@code operaton.bpm.*} 引擎配置区分）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.bpm")
public class KiwiBpmProperties {

    /**
     * 是否对外暴露 Operaton {@code /engine-rest} HTTP（Jersey Servlet）。默认关闭。
     */
    private boolean engineRestHttpEnabled;
}
