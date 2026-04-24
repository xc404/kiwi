package com.kiwi.project.bpm.integration;

import com.kiwi.project.bpm.service.BpmProcessStartService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.rest.dto.runtime.ProcessInstanceDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 供 cryoEMS 等服务启动流程；鉴权与全局 API 一致（{@code Authorization: Bearer …}，含个人中心签发的长期 Token）。
 */
@RestController
@RequestMapping("/bpm/integration/process")
@RequiredArgsConstructor
public class BpmMachineIntegrationCtl {

    private final BpmProcessStartService bpmProcessStartService;

    @Data
    public static class MachineStartBody {
        private Map<String, Object> variables;
    }

    @PostMapping("{id}/start")
    public ProcessInstanceDto startForMachine(
            @PathVariable String id,
            @RequestBody(required = false) MachineStartBody body) {
        Map<String, Object> variables = body != null ? body.getVariables() : null;
        return bpmProcessStartService.start(id, variables);
    }
}
