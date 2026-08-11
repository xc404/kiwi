package com.kiwi.bpmn.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 写工作流配置（{@code kiwi.ai.write-workflow.*}）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.ai.write-workflow")
public class AssistantProperties {

    /** 是否启用写工作流 Java 管线；关闭时助手保持原 Chat/MCP 行为 */
    private boolean enabled = false;
    /** 修复轮次上限 */
    private int maxRepairRounds = 3;
    /** Catalog 已装组件 Top-N */
    private int catalogInstalledTopN = 40;
    /** Catalog 模板摘要 Top-N */
    private int catalogTemplateTopN = 8;
    /** Catalog 可装（未装）条目 Top-N */
    private int catalogInstallableTopN = 15;
}
