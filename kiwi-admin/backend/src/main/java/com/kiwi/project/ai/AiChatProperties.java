package com.kiwi.project.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 业务开关；模型连接参数见 {@code spring.ai.deepseek.*}（DeepSeek）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.ai")
public class AiChatProperties {

    /**
     * 是否启用 AI 对话接口；关闭时请求会失败并提示。
     */
    private boolean enabled = true;

    /** 单会话最多保留的 user/assistant 消息条数 */
    private int conversationMaxMessages = 200;

    /** 单条消息 content 最大字符数（超出截断） */
    private int conversationMaxContentLength = 32_000;

    /**
     * 场景驱动「AI 写工作流」内部流程编排（dogfood）。
     */
    private WorkflowAuthoring workflowAuthoring = new WorkflowAuthoring();

    @Data
    public static class WorkflowAuthoring {
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
}
