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
@Tag(name = "BPM 设计器 Agent", description = "EditPlan Agent：POST 创建 + GET 状态 + POST action + GET events")
@Slf4j
public class DesignerAgentCtl extends BaseCtl {

    private final DesignerAgentSessionService sessionService;
    private final DesignerAgentProperties properties;
    private final ObjectMapper objectMapper;

    @Operation(operationId = "designerAgent_createRun", summary = "创建 Agent run 并异步启动 Graph")
    @PostMapping("/runs")
    public DesignerAgentRunStatus createRun(@RequestBody StartRunRequest request) {
        String userId = getCurrentUser().getId();
        return sessionService.createRun(
                request.getScenario(),
                request.getTargetProcessId(),
                request.getSelectedElementId(),
                request.getBaseBpmnXml(),
                userId);
    }

    @Operation(operationId = "designerAgent_followUp", summary = "在同一会话中续聊（follow-up）")
    @PostMapping("/runs/{runId}/follow-up")
    public DesignerAgentRunStatus followUp(
            @PathVariable String runId,
            @RequestBody FollowUpRequest request) {
        return sessionService.followUp(
                runId,
                request.getMessage(),
                request.getSelectedElementId(),
                request.getCanvasBpmnXml());
    }

    @Operation(operationId = "designerAgent_clearSession", summary = "清空目标流程的 Agent 会话")
    @PostMapping("/sessions/clear")
    public DesignerAgentRunStatus clearSession(@RequestParam String targetProcessId) {
        return sessionService.clearSession(targetProcessId);
    }

    @Operation(operationId = "designerAgent_eventStream", summary = "订阅 run 事件流（仅日志/思考，不含状态变更语义）")
    @GetMapping(value = "/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter eventStream(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        sessionService.bindStream(runId, event -> sendEvent(emitter, event));
        emitter.onCompletion(() -> log.debug("events stream completed runId={}", runId));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    @Operation(operationId = "designerAgent_submitAction", summary = "提交人机操作（plan / preview / ask）")
    @PostMapping("/runs/{runId}/actions")
    public DesignerAgentRunStatus submitAction(
            @PathVariable String runId,
            @RequestBody DesignerAgentRunActionRequest body) {
        return sessionService.submitAction(runId, body);
    }

    @Operation(operationId = "designerAgent_statusByTarget", summary = "按目标流程查询 Agent run 状态")
    @GetMapping("/by-target")
    public DesignerAgentRunStatus statusByTarget(@RequestParam String targetProcessId) {
        return sessionService.statusByTarget(targetProcessId);
    }

    @Operation(operationId = "designerAgent_status", summary = "按 runId 查询状态（权威）")
    @GetMapping("/runs/{runId}")
    public DesignerAgentRunStatus status(@PathVariable String runId) {
        return sessionService.statusByRunId(runId);
    }

    /** @deprecated 使用 POST {@code /runs} + GET {@code /runs/{id}/events} */
    @Deprecated
    @Operation(operationId = "designerAgent_startStream", summary = "（兼容）启动 run 并推送事件")
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
            return emitter;
        }
        sessionService.startRunExecution(run.getRunId());
        emitter.onCompletion(() -> log.debug("legacy stream completed runId={}", run.getRunId()));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    /** @deprecated 使用 GET {@code /runs/{id}/events} */
    @Deprecated
    @Operation(operationId = "designerAgent_resumeStream", summary = "（兼容）续订事件流")
    @PostMapping(value = "/runs/{runId}/stream/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeStream(
            @PathVariable String runId,
            @RequestParam(defaultValue = "true") @Schema(description = "是否重放历史事件") boolean replay) {
        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMs());
        DesignerAgentRun run = sessionService.bindStream(runId, event -> sendEvent(emitter, event));
        try {
            emitter.send(SseEmitter.event()
                    .name("run_resumed")
                    .data(objectMapper.writeValueAsString(sessionService.statusByRunId(runId))));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }
        if (replay) {
            sessionService.replayBufferedEvents(run, event -> sendEvent(emitter, event));
        }
        emitter.onCompletion(() -> log.debug("legacy resume completed runId={}", runId));
        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    /** @deprecated 使用 POST {@code /runs/{id}/actions} type=confirm_plan */
    @Deprecated
    @Operation(operationId = "designerAgent_confirmPlan", summary = "（兼容）确认或拒绝 EditPlan")
    @PostMapping("/runs/{runId}/confirm-plan")
    public DesignerAgentRunStatus confirmPlan(
            @PathVariable String runId,
            @RequestBody ConfirmPlanRequest body) {
        DesignerAgentRunActionRequest action = new DesignerAgentRunActionRequest();
        action.setType("confirm_plan");
        action.setConfirmed(body != null ? body.getConfirmed() : null);
        action.setEditedPlanJson(body != null ? body.getEditedPlanJson() : null);
        return sessionService.submitAction(runId, action);
    }

    /** @deprecated 使用 POST {@code /runs/{id}/actions} type=confirm_preview */
    @Deprecated
    @Operation(operationId = "designerAgent_confirmPreview", summary = "（兼容）确认或拒绝预览")
    @PostMapping("/runs/{runId}/confirm-preview")
    public DesignerAgentRunStatus confirmPreview(
            @PathVariable String runId,
            @RequestBody ConfirmPreviewRequest body) {
        DesignerAgentRunActionRequest action = new DesignerAgentRunActionRequest();
        action.setType("confirm_preview");
        action.setConfirmed(body != null ? body.getConfirmed() : null);
        return sessionService.submitAction(runId, action);
    }

    /** @deprecated 使用 POST {@code /runs/{id}/actions} type=answer */
    @Deprecated
    @Operation(operationId = "designerAgent_answer", summary = "（兼容）提交追问")
    @PostMapping("/runs/{runId}/answer")
    public DesignerAgentRunStatus answer(
            @PathVariable String runId,
            @RequestBody AnswerRequest body) {
        DesignerAgentRunActionRequest action = new DesignerAgentRunActionRequest();
        action.setType("answer");
        action.setUserAnswer(body != null ? body.getUserAnswer() : null);
        return sessionService.submitAction(runId, action);
    }

    private void sendEvent(SseEmitter emitter, AgentStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType() != null ? event.getType() : "message")
                    .data(objectMapper.writeValueAsString(event)));
            if ("done".equals(event.getType()) || "error".equals(event.getType())) {
                emitter.complete();
            }
        } catch (IllegalStateException e) {
            log.debug("event stream already completed, skip type={}", event.getType());
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

    @Data
    @Schema(description = "会话续聊")
    public static class FollowUpRequest {
        @Schema(description = "用户消息", requiredMode = Schema.RequiredMode.REQUIRED)
        private String message;
        @Schema(description = "画布选中元素 id")
        private String selectedElementId;
        @Schema(description = "当前 BPMN XML")
        private String canvasBpmnXml;
    }
}
