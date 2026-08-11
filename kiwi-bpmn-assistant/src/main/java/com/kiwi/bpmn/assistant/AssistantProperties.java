package com.kiwi.bpmn.assistant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 写工作流编排配置（与 admin {@code kiwi.ai.workflow-authoring.*} 对齐）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.ai.workflow-authoring")
public class AssistantProperties {

    /** 是否启用元流程编排；关闭时助手保持原行为 */
    private boolean enabled = false;
    /** 内部流程定义 key */
    private String processDefinitionKey = "kiwi_ai_workflow_authoring";
    /** 修复轮次上限 */
    private int maxRepairRounds = 3;
    /** Catalog 已装组件 Top-N */
    private int catalogInstalledTopN = 40;
    /** Catalog 模板摘要 Top-N */
    private int catalogTemplateTopN = 8;
    /** Catalog 可装（未装）条目 Top-N */
    private int catalogInstallableTopN = 15;
}
