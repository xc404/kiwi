package com.kiwi.bpmn.designer.agent.model;

/**
 * Agent run 阶段常量。
 */
public final class AgentRunStage {

    public static final String Ingest = "ingest";
    public static final String Think = "think";
    public static final String Tool = "tool";
    public static final String PlanReady = "plan_ready";
    public static final String AwaitPlan = "await_plan";
    public static final String Apply = "apply";
    public static final String Validate = "validate";
    public static final String Repair = "repair";
    public static final String AwaitPreview = "await_preview";
    public static final String AwaitInstall = "await_install";
    public static final String AwaitAsk = "await_ask";
    public static final String Save = "save";
    public static final String Done = "done";
    public static final String Error = "error";

    private AgentRunStage() {
    }
}
