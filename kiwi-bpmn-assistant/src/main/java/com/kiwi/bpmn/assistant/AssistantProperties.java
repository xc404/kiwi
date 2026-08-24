package com.kiwi.bpmn.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Plan IR 校验配置（历史前缀 {@code kiwi.ai.write-workflow.*}）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.ai.write-workflow")
public class AssistantProperties {

    /** 修复轮次上限 */
    private int maxRepairRounds = 3;
}
