package com.kiwi.project.bpm.harness;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.bpmn.designer.harness.DesignerHarnessConfig;
import com.kiwi.bpmn.designer.harness.DesignerHarnessSessionService;
import com.kiwi.bpmn.designer.harness.DesignerHarnessStatus;
import com.kiwi.bpmn.designer.harness.DesignerHarnessTurnRequest;
import com.kiwi.framework.ctl.BaseCtl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/bpm/designer-harness")
@Tag(name = "BPM 设计器对话 Agent")
public class DesignerHarnessCtl extends BaseCtl {

    private final DesignerHarnessSessionService sessionService;

    @GetMapping("/config")
    @Operation(operationId = "designerHarness_getConfig", summary = "设计器对话 Agent 是否启用")
    public DesignerHarnessConfig config() {
        return sessionService.config();
    }

    @GetMapping("/sessions")
    @Operation(operationId = "designerHarness_getSession", summary = "按流程读取设计器对话会话")
    public DesignerHarnessStatus getSession(@RequestParam String targetProcessId) {
        return sessionService.status(targetProcessId, getCurrentUserId());
    }

    @PostMapping("/sessions/clear")
    @Operation(operationId = "designerHarness_clearSession", summary = "清空当前流程的设计器对话")
    public DesignerHarnessStatus clear(@RequestParam String targetProcessId) {
        return sessionService.clear(targetProcessId, getCurrentUserId());
    }

    @PostMapping("/sessions/turns")
    @Operation(operationId = "designerHarness_postTurn", summary = "发送一句用户话并跑一轮工具循环")
    public DesignerHarnessStatus postTurn(@RequestBody DesignerHarnessTurnRequest request) {
        return sessionService.turn(request, getCurrentUserId());
    }
}