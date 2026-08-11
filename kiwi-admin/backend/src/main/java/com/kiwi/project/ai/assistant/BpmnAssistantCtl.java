package com.kiwi.project.ai.assistant;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.bpmn.assistant.AssistantProcessService;
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

import java.util.Map;

@SaCheckLogin
@RestController
@RequestMapping("/ai/workflow-authoring")
@RequiredArgsConstructor
@Tag(name = "AI 写工作流", description = "场景驱动内部流程编排桥接")
public class BpmnAssistantCtl extends BaseCtl {

    private final AssistantProcessService authoringProcessService;

    @Operation(operationId = "aiAuthoring_start", summary = "启动 AI 写工作流编排实例")
    @PostMapping("/start")
    public AssistantProcessService.StartResult start(@RequestBody StartRequest request) {
        return authoringProcessService.start(
                request.getScenario(),
                request.getTargetProcessId(),
                request.getSelectedElementId(),
                request.getBaseBpmnXml(),
                getCurrentUser().getId());
    }

    @Operation(operationId = "aiAuthoring_status", summary = "查询编排实例状态")
    @GetMapping("/{processInstanceId}")
    public AssistantProcessService.StatusResult status(@PathVariable String processInstanceId) {
        return authoringProcessService.status(processInstanceId);
    }

    @Operation(operationId = "aiAuthoring_statusByTarget", summary = "按目标流程查询活跃编排状态")
    @GetMapping("/by-target")
    public AssistantProcessService.StatusResult statusByTarget(
            @RequestParam String targetProcessId) {
        return authoringProcessService.statusByTargetProcess(targetProcessId);
    }

    @Operation(operationId = "aiAuthoring_completeTask", summary = "完成预览/安装/追问 User Task")
    @PostMapping("/tasks/{taskId}/complete")
    public AssistantProcessService.StatusResult completeTask(
            @PathVariable String taskId,
            @RequestBody(required = false) CompleteTaskRequest body) {
        Map<String, Object> vars = body != null && body.getVariables() != null
                ? body.getVariables()
                : Map.of();
        return authoringProcessService.completeTask(taskId, vars);
    }

    @Data
    @Schema(description = "启动 AI 写工作流请求")
    public static class StartRequest {
        @Schema(description = "应用场景描述", requiredMode = Schema.RequiredMode.REQUIRED)
        private String scenario;
        @Schema(description = "目标流程定义 id", requiredMode = Schema.RequiredMode.REQUIRED)
        private String targetProcessId;
        @Schema(description = "画布选中元素 id")
        private String selectedElementId;
        @Schema(description = "当前画布 BPMN XML（有则在此基础上修改）")
        private String baseBpmnXml;
    }

    @Data
    @Schema(description = "完成 User Task 请求")
    public static class CompleteTaskRequest {
        @Schema(description = "完成任务时写入的流程变量，如 previewConfirmed、installAccepted、userAnswer")
        private Map<String, Object> variables;
    }
}
