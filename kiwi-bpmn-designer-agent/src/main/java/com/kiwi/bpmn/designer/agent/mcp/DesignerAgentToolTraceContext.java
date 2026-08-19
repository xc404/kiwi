package com.kiwi.bpmn.designer.agent.mcp;

import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentRun;
import org.apache.commons.lang3.StringUtils;

/**
 * 当前线程绑定的 Agent run，供 MCP 工具调用轨迹（tool_start / tool_end）上报 SSE。
 */
public final class DesignerAgentToolTraceContext {

    private static final ThreadLocal<DesignerAgentRun> Current = new ThreadLocal<>();

    private DesignerAgentToolTraceContext() {
    }

    public static void bind(DesignerAgentRun run) {
        if (run == null) {
            clear();
        } else {
            Current.set(run);
        }
    }

    public static void clear() {
        Current.remove();
    }

    public static void emitToolStart(String toolName, String argsPreview) {
        DesignerAgentRun run = Current.get();
        if (run == null || StringUtils.isBlank(toolName)) {
            return;
        }
        run.setToolStepCount(run.getToolStepCount() + 1);
        run.setStage(AgentRunStage.Tool);
        AgentStreamEvent event = AgentStreamEvent.of("tool_start");
        event.setStage(AgentRunStage.Tool);
        event.setToolName(toolName);
        event.setArgsPreview(truncate(argsPreview, 500));
        run.emit(event);
    }

    public static void emitToolEnd(String toolName, String summary) {
        DesignerAgentRun run = Current.get();
        if (run == null || StringUtils.isBlank(toolName)) {
            return;
        }
        AgentStreamEvent event = AgentStreamEvent.of("tool_end");
        event.setStage(AgentRunStage.Tool);
        event.setToolName(toolName);
        event.setSummary(truncate(summary, 500));
        run.emit(event);
    }

    public static String summarizeToolResult(String result) {
        if (StringUtils.isBlank(result)) {
            return "(empty)";
        }
        String trimmed = result.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "json len=" + trimmed.length();
        }
        if (trimmed.length() <= 120) {
            return trimmed;
        }
        return trimmed.substring(0, 120) + "…";
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
