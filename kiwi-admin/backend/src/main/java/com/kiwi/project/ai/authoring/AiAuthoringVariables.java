package com.kiwi.project.ai.authoring;

/**
 * {@code kiwi_ai_workflow_authoring} 流程变量契约。
 */
public final class AiAuthoringVariables {

    public static final String Scenario = "scenario";
    public static final String TargetProcessId = "targetProcessId";
    public static final String SelectedElementId = "selectedElementId";
    public static final String InitiatorUserId = "initiatorUserId";
    public static final String KeywordsJson = "keywordsJson";
    public static final String CatalogJson = "catalogJson";
    public static final String PlanIrJson = "planIrJson";
    public static final String CandidateXml = "candidateXml";
    /** 启动时画布上的原始 BPMN（生成步骤据此修改；可与 candidate 相同） */
    public static final String BaseBpmnXml = "baseBpmnXml";
    /** 面向用户的自然语言说明（来自生成/修复大模型） */
    public static final String AssistantReply = "assistantReply";
    public static final String IssuesJson = "issuesJson";
    public static final String DispatchCode = "dispatchCode";
    public static final String RepairRound = "repairRound";
    public static final String PluginHintJson = "pluginHintJson";
    public static final String AskMessage = "askMessage";
    public static final String Stage = "stage";
    public static final String PreviewConfirmed = "previewConfirmed";
    public static final String InstallAccepted = "installAccepted";
    public static final String UserAnswer = "userAnswer";
    public static final String ErrorMessage = "errorMessage";

    public static final String DispatchPass = "PASS";
    public static final String DispatchRepair = "REPAIR";
    public static final String DispatchInstall = "INSTALL";
    public static final String DispatchAsk = "ASK";

    public static final String StageExtract = "extract";
    public static final String StageCatalog = "catalog";
    public static final String StageGenerate = "generate";
    public static final String StageValidate = "validate";
    public static final String StageRepair = "repair";
    public static final String StageInstall = "install";
    public static final String StageAwaitPreview = "await_preview";
    public static final String StageAwaitInstall = "await_install";
    public static final String StageAwaitAsk = "await_ask";
    public static final String StageSave = "save";
    public static final String StageDone = "done";

    private AiAuthoringVariables() {
    }
}
