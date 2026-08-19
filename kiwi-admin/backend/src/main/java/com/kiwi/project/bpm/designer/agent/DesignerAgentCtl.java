package com.kiwi.project.bpm.designer.agent;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentRun;
import com.kiwi.framework.ctl.BaseCtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@SaCheckLogin
@RestController
@RequestMapping("/bpm/designer-agent")
@RequiredArgsConstructor
@Tag(name = "BPM 设计器 Agent", description = "Greenfield 设计器 Agent：EditPlan + SSE")
@Slf4j
public class DesignerAgentCtl extends BaseCtl {

    private final DesignerAgentSessionService sessionService;
    private final DesignerAgentProperties properties;
    private final ObjectMapper objectMapper;

    @Operation(operationId = "designerAgent_startStream", summary = "启动 Agent run 并通过 SSE 推送事件")
    @PostMapping(value = "/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter startStream(@RequestBody StartRunRequest request) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        String userId = getCurrentUser().getId();
        DesignerAgentRun run = sessionService.startRun(
                request.getScenario(),
                request.getTargetProcessId(),
                request.getSelectedElementId(),
                request.getBaseBpmnXml(),
                userId,
                event -> sendEvent(emitter, event));
        try {
            emitter.send(SseEmitter.event()
                    .name("run_started")
                    .data(objectMapper.writeValueAsString(sessionService.statusByRunId(run.getRunId()))));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        emitter.onCompletion(() -> log.debug("SSE completed runId={}", run.getRunId()));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    @Operation(operationId = "designerAgent_statusByTarget", summary = "按目标流程查询 Agent run 状态")
    @GetMapping("/by-target")
    public DesignerAgentRunStatus statusByTarget(@RequestParam String targetProcessId) {
        return sessionService.statusByTarget(targetProcessId);
    }

    @Operation(operationId = "designerAgent_status", summary = "按 runId 查询状态")
    @GetMapping("/runs/{runId}")
    public DesignerAgentRunStatus status(@PathVariable String runId) {
        return sessionService.statusByRunId(runId);
    }

    @Operation(operationId = "designerAgent_resumeStream", summary = "续订 Agent run 的 SSE 事件流")
    @PostMapping(value = "/runs/{runId}/stream/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeStream(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        DesignerAgentRun run = sessionService.attachStream(
                runId,
                event -> sendEvent(emitter, event));
        try {
            emitter.send(SseEmitter.event()
                    .name("run_resumed")
                    .data(objectMapper.writeValueAsString(sessionService.statusByRunId(run.getRunId()))));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        emitter.onCompletion(() -> log.debug("SSE resumed completed runId={}", runId));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    @Operation(operationId = "designerAgent_confirmPlan", summary = "确认或拒绝 EditPlan")
    @PostMapping("/runs/{runId}/confirm-plan")
    public DesignerAgentRunStatus confirmPlan(
            @PathVariable String runId,
            @RequestBody ConfirmPlanRequest body) {
        boolean confirmed = body != null && Boolean.TRUE.equals(body.getConfirmed());
        return sessionService.confirmPlan(runId, confirmed, body != null ? body.getEditedPlanJson() : null);
    }

    @Operation(operationId = "designerAgent_confirmPreview", summary = "确认或拒绝预览（确认则保存 BPMN）")
    @PostMapping("/runs/{runId}/confirm-preview")
    public DesignerAgentRunStatus confirmPreview(
            @PathVariable String runId,
            @RequestBody ConfirmPreviewRequest body) {
        boolean confirmed = body != null && Boolean.TRUE.equals(body.getConfirmed());
        return sessionService.confirmPreview(runId, confirmed);
    }

    @Operation(operationId = "designerAgent_answer", summary = "提交追问补充说明")
    @PostMapping("/runs/{runId}/answer")
    public DesignerAgentRunStatus answer(
            @PathVariable String runId,
            @RequestBody AnswerRequest body) {
        return sessionService.answerAsk(runId, body != null ? body.getUserAnswer() : null);
    }

    private void sendEvent(SseEmitter emitter, AgentStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType() != null ? event.getType() : "message")
                    .data(objectMapper.writeValueAsString(event)));
            if ("done".equals(event.getType()) || "error".equals(event.getType())) {
                emitter.complete();
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    @Data
    @Schema(description = "启动 Agent run")
    public static class StartRunRequest {
        @Schema(description = "用户场景/指令", requiredMode = Schema.RequiredMode.REQUIRED)
        private String scenario;
        @Schema(description = "目标流程 id", requiredMode = Schema.RequiredMode.REQUIRED)
        private String targetProcessId;
        @Schema(description = "画布选中元素 id")
        private String selectedElementId;
        @Schema(description = "当前 BPMN XML")
        private String baseBpmnXml;
    }

    @Data
    @Schema(description = "Plan 确认")
    public static class ConfirmPlanRequest {
        private Boolean confirmed;
        private String editedPlanJson;
    }

    @Data
    @Schema(description = "预览确认")
    public static class ConfirmPreviewRequest {
        private Boolean confirmed;
    }

    @Data
    @Schema(description = "追问回答")
    public static class AnswerRequest {
        private String userAnswer;
    }
}
