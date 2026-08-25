package com.kiwi.project.bpm.designer.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Designer Agent run 人机操作")
public class DesignerAgentRunActionRequest {

    @Schema(
            description = "操作类型：confirm_plan | confirm_preview | answer",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "confirm_plan / confirm_preview：是否确认")
    private Boolean confirmed;

    @Schema(description = "confirm_plan：用户编辑后的 EditPlan JSON")
    private String editedPlanJson;

    @Schema(description = "answer：用户补充说明")
    private String userAnswer;

    @Schema(description = "当前画布 BPMN XML（用户可能在预览/等待期间手动改图，以此为准）")
    private String canvasBpmnXml;
}
