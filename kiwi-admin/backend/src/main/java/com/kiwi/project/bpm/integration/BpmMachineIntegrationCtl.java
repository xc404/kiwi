package com.kiwi.project.bpm.integration;

import cn.dev33.satoken.annotation.SaIgnore;
import com.kiwi.project.bpm.service.BpmProcessStartService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.rest.dto.runtime.ProcessInstanceDto;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 供 cryoEMS 等服务以共享密钥启动流程（不经用户登录态）。
 */
@RestController
@RequestMapping("/bpm/integration/process")
@RequiredArgsConstructor
public class BpmMachineIntegrationCtl {

    private final BpmProcessStartService bpmProcessStartService;
    private final KiwiIntegrationProperties integrationProperties;

    @Data
    public static class MachineStartBody {
        private Map<String, Object> variables;
    }

    @SaIgnore
    @PostMapping("{id}/start")
    public ProcessInstanceDto startForMachine(
            @PathVariable String id,
            @RequestBody(required = false) MachineStartBody body,
            @RequestHeader(value = "X-Kiwi-Integration-Secret", required = false) String secret) {
        String expected = integrationProperties.getMachine().getSecret();
        if (!StringUtils.hasText(expected) || !expected.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "integration secret mismatch");
        }
        Map<String, Object> variables = body != null ? body.getVariables() : null;
        return bpmProcessStartService.start(id, variables);
    }
}
