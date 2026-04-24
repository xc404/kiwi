package com.kiwi.project.bpm.integration;

import cn.dev33.satoken.annotation.SaIgnore;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 供 cryoEMS 等以共享密钥查询流程实例状态（不经用户登录态）。
 */
@RestController
@RequestMapping("/bpm/integration/process-instances")
@RequiredArgsConstructor
public class BpmMachineProcessInstanceIntegrationCtl {

    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final KiwiIntegrationProperties integrationProperties;

    @SaIgnore
    @GetMapping("{instanceId}/state")
    public ProcessInstanceIntegrationDto stateForMachine(
            @PathVariable String instanceId,
            @RequestHeader(value = "X-Kiwi-Integration-Secret", required = false) String secret) {
        validateSecret(secret);

        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (pi != null) {
            ProcessInstanceIntegrationDto dto = new ProcessInstanceIntegrationDto();
            dto.setId(pi.getId());
            dto.setEnded(false);
            dto.setSuspended(pi.isSuspended());
            dto.setState(pi.isSuspended() ? "SUSPENDED" : "RUNNING");
            return dto;
        }

        HistoricProcessInstance hip = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        if (hip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "process instance not found");
        }

        ProcessInstanceIntegrationDto dto = new ProcessInstanceIntegrationDto();
        dto.setId(hip.getId());
        dto.setEndTime(hip.getEndTime());
        dto.setDeleteReason(hip.getDeleteReason());
        if (hip.getEndTime() == null) {
            dto.setEnded(false);
            dto.setState("ACTIVE");
            return dto;
        }
        dto.setEnded(true);
        boolean canceled = StringUtils.hasText(hip.getDeleteReason());
        dto.setState(canceled ? "CANCELED" : "COMPLETED");
        return dto;
    }

    private void validateSecret(String secret) {
        String expected = integrationProperties.getMachine().getSecret();
        if (!StringUtils.hasText(expected) || !expected.equals(secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "integration secret mismatch");
        }
    }
}
