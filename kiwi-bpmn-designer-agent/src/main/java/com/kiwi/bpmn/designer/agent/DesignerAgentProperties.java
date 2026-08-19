package com.kiwi.bpmn.designer.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * BPM 设计器 Agent 配置（{@code kiwi.bpm.designer-agent.*}）。
 */
@Data
@ConfigurationProperties(prefix = "kiwi.bpm.designer-agent")
public class DesignerAgentProperties {

    /** 是否启用设计器 Agent（替代旧 write-workflow / bpm-ai-chat 改图） */
    private boolean enabled = false;
    /** 是否默认进入 Plan 审阅闸门 */
    private boolean planMode = true;
    /** 简单操作自动跳过 Plan 闸门 */
    private boolean planModeSkipSimple = true;
    /** 单次 run 最大 MCP/工具步数 */
    private int maxToolSteps = 20;
    /** SSE 超时（毫秒） */
    private long sseTimeoutMs = 300_000L;
    /** 修复轮次上限 */
    private int maxRepairRounds = 3;
}
