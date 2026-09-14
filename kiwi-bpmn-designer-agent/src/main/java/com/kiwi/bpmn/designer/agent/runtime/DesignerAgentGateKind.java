package com.kiwi.bpmn.designer.agent.runtime;

/** 人机闸门：由 {@link DesignerAgentGateIntentClassifier} 解析用户自然语言意图。 */
public enum DesignerAgentGateKind {
    /** 等待批准变更计划（await_plan） */
    PLAN,
    /** 等待预览确认（await_preview） */
    PREVIEW
}
