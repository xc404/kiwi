package com.kiwi.bpmn.designer.agent.runtime;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.stereotype.Component;

/**
 * 将进程内 {@link DesignerAgentRun}（SSE sink）挂到 Graph {@code RunnableConfig.context}。
 */
@Component
public class DesignerAgentRunBinding {

    public static final String ContextKey = "designerAgentRun";

    public RunnableConfig bind(String runId, DesignerAgentRun run) {
        RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
        if (run != null) {
            config.context().put(ContextKey, run);
        }
        return config;
    }

    public RunnableConfig resume(String runId, DesignerAgentRun run) {
        RunnableConfig config = RunnableConfig.builder().threadId(runId).resume().build();
        if (run != null) {
            config.context().put(ContextKey, run);
        }
        return config;
    }

    public DesignerAgentRun requireRun(RunnableConfig config, DesignerAgentRun fallback) {
        if (config != null && config.context() != null) {
            Object bound = config.context().get(ContextKey);
            if (bound instanceof DesignerAgentRun run) {
                return run;
            }
        }
        return fallback;
    }
}
