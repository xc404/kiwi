package com.kiwi.bpmn.designer.agent.runtime;

/**
 * Graph {@code OverAllState} 与 {@link DesignerAgentRun} 对齐的扁平 key。
 */
public final class DesignerAgentStateKeys {

    public static final String RunId = "runId";
    public static final String TargetProcessId = "targetProcessId";
    public static final String InitiatorUserId = "initiatorUserId";
    public static final String UserScenario = "userScenario";
    public static final String SelectedElementId = "selectedElementId";
    public static final String BaseBpmnXml = "baseBpmnXml";
    public static final String Stage = "stage";
    public static final String Active = "active";
    public static final String EditPlanJson = "editPlanJson";
    public static final String PlanDisplayJson = "planDisplayJson";
    public static final String CandidateXml = "candidateXml";
    public static final String AssistantReply = "assistantReply";
    public static final String IssuesJson = "issuesJson";
    public static final String AskMessage = "askMessage";
    public static final String PluginHintJson = "pluginHintJson";
    public static final String ErrorMessage = "errorMessage";
    public static final String RepairRound = "repairRound";
    public static final String ToolStepCount = "toolStepCount";
    public static final String PlanSkipped = "planSkipped";
    public static final String PlanConfirmed = "planConfirmed";
    public static final String PreviewConfirmed = "previewConfirmed";
    public static final String PersistRequested = "persistRequested";
    public static final String Route = "route";

    public static final String RouteExplain = "explain";
    public static final String RouteGenerate = "generate";
    public static final String RouteApply = "apply";
    public static final String RouteHumanPlan = "human_plan";
    public static final String RouteValidate = "validate";
    public static final String RouteHumanPreview = "human_preview";
    public static final String RouteHumanAsk = "human_ask";
    public static final String RouteHumanInstall = "human_install";
    public static final String RoutePersistPreview = "persist_preview";
    public static final String RouteFail = "fail";
    public static final String RouteEnd = "end";

    public static final String[] AllKeys = {
            RunId, TargetProcessId, InitiatorUserId, UserScenario, SelectedElementId, BaseBpmnXml,
            Stage, Active, EditPlanJson, PlanDisplayJson, CandidateXml, AssistantReply, IssuesJson,
            AskMessage, PluginHintJson, ErrorMessage, RepairRound, ToolStepCount, PlanSkipped,
            PlanConfirmed, PreviewConfirmed, PersistRequested, Route
    };

    private DesignerAgentStateKeys() {
    }
}
