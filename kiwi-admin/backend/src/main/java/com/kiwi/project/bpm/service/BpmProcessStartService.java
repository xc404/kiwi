package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.rest.dto.runtime.ProcessInstanceDto;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 启动已部署的流程定义（可选流程变量）。
 */
@Service
@RequiredArgsConstructor
public class BpmProcessStartService {

    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final ProcessEngine processEngine;

    public ProcessInstanceDto start(String bpmProcessId, Map<String, Object> variables) {
        BpmProcess bpmProcess = this.bpmProcessDefinitionDao.findById(bpmProcessId).orElseThrow();
        String deployedProcessDefinitionId = bpmProcess.getDeployedProcessDefinitionId();
        if (deployedProcessDefinitionId == null) {
            throw new IllegalStateException("流程未部署");
        }
        enforceMaxRunningInstances(bpmProcess, deployedProcessDefinitionId);
        ProcessInstance processInstance;
        if (variables == null || variables.isEmpty()) {
            processInstance = processEngine.getRuntimeService()
                    .startProcessInstanceById(deployedProcessDefinitionId);
        } else {
            processInstance = processEngine.getRuntimeService()
                    .startProcessInstanceById(deployedProcessDefinitionId, variables);
        }
        return new ProcessInstanceDto(processInstance);
    }

    /**
     * 若 {@link BpmProcess#getMaxProcessInstances()} 大于 0，则统计当前 Camunda 运行中实例数（按流程定义 key），
     * 达到上限则拒绝启动（HTTP 429）。始终走引擎实时统计。
     */
    private void enforceMaxRunningInstances(BpmProcess bpmProcess, String deployedProcessDefinitionId) {
        Integer max = bpmProcess.getMaxProcessInstances();
        if (max == null || max <= 0) {
            return;
        }
        long running = countRunningForDeployedDefinitionId(deployedProcessDefinitionId);
        if (running >= max) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    String.format(
                            "流程「%s」运行中实例已达上限：当前 %d，上限 %d",
                            bpmProcess.getName() != null ? bpmProcess.getName() : bpmProcess.getId(),
                            running,
                            max));
        }
    }

    private long countRunningForDeployedDefinitionId(String deployedProcessDefinitionId) {
        ProcessDefinition processDefinition = processEngine.getRepositoryService()
                .getProcessDefinition(deployedProcessDefinitionId);
        String key = processDefinition.getKey();
        return processEngine.getRuntimeService()
                .createProcessInstanceQuery()
                .processDefinitionKey(key)
                .count();
    }
}
