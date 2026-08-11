package com.kiwi.bpmn.assistant;

import org.operaton.bpm.engine.delegate.DelegateExecution;

/**
 * 流程变量读写小工具（library-style）。
 */
public final class AssistantExecutionUtils {

    private AssistantExecutionUtils() {
    }

    public static String str(DelegateExecution execution, String name) {
        Object v = execution.getVariable(name);
        return v == null ? null : String.valueOf(v);
    }
}
