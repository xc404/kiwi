package com.kiwi.project.ai.assistant;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.bpmn.assistant.WriteWorkflowStatus;
import com.kiwi.framework.ctl.BaseCtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SaCheckLogin
@RestController
@RequestMapping("/ai/write-workflow")
@RequiredArgsConstructor
@Tag(name = "AI 写工作流", description = "意图分派 + Java 管线 + 会话状态")
public class WriteWorkflowCtl extends BaseCtl {

    private final WriteWorkflowSessionService sessionService;

    @Operation(operationId = "writeWorkflow_start", summary = "启动一轮写工作流（强制 modify）")
    @PostMapping("/start")
    public WriteWorkflowStatus start(@RequestBody StartRequest request) {
        return sessionService.start(
                request.getScenario(),
                request.getTargetProcessId(),
                request.getSelectedElementId(),
                request.getBaseBpmnXml(),
                getCurrentUser().getId());
    }

    @Operation(operationId = "writeWorkflow_statusByTarget", summary = "按目标流程查询会话状态")
    @GetMapping("/by-target")
    public WriteWorkflowStatus statusByTarget(@RequestParam String targetProcessId) {
        return sessionService.statusByTarget(targetProcessId);
    }

    @Operation(operationId = "writeWorkflow_status", summary = "按会话 id 查询状态")
    @GetMapping("/sessions/{sessionId}")
    public WriteWorkflowStatus status(@PathVariable String sessionId) {
        return sessionService.statusBySessionId(sessionId);
    }

    @Operation(operationId = "writeWorkflow_confirmPreview", summary = "确认或拒绝预览")
    @PostMapping("/sessions/{sessionId}/confirm-preview")
    public WriteWorkflowStatus confirmPreview(
            @PathVariable String sessionId,
            @RequestBody ConfirmPreviewRequest body) {
        boolean confirmed = body != null && Boolean.TRUE.equals(body.getConfirmed());
        return sessionService.confirmPreview(sessionId, confirmed);
    }

    @Operation(operationId = "writeWorkflow_confirmInstall", summary = "确认或拒绝安装插件")
    @PostMapping("/sessions/{sessionId}/confirm-install")
    public WriteWorkflowStatus confirmInstall(
            @PathVariable String sessionId,
            @RequestBody ConfirmInstallRequest body) {
        boolean accepted = body != null && Boolean.TRUE.equals(body.getAccepted());
        return sessionService.confirmInstall(sessionId, accepted);
    }

    @Operation(operationId = "writeWorkflow_answer", summary = "提交追问补充说明")
    @PostMapping("/sessions/{sessionId}/answer")
    public WriteWorkflowStatus answer(
            @PathVariable String sessionId,
            @RequestBody AnswerRequest body) {
        return sessionService.answerAsk(sessionId, body != null ? body.getUserAnswer() : null);
    }

    @Data
    @Schema(description = "启动写工作流请求")
    public static class StartRequest {
        @Schema(description = "场景描述", requiredMode = Schema.RequiredMode.REQUIRED)
        private String scenario;
        @Schema(description = "目标流程 id", requiredMode = Schema.RequiredMode.REQUIRED)
        private String targetProcessId;
        @Schema(description = "画布选中元素 id")
        private String selectedElementId;
        @Schema(description = "当前画布 BPMN XML")
        private String baseBpmnXml;
    }

    @Data
    @Schema(description = "预览确认")
    public static class ConfirmPreviewRequest {
        private Boolean confirmed;
    }

    @Data
    @Schema(description = "安装确认")
    public static class ConfirmInstallRequest {
        private Boolean accepted;
    }

    @Data
    @Schema(description = "追问回答")
    public static class AnswerRequest {
        private String userAnswer;
    }
}
