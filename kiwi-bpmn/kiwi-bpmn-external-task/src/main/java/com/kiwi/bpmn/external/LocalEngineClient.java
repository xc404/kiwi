package com.kiwi.bpmn.external;

import com.kiwi.bpmn.external.utils.DtoUtils;
import org.camunda.bpm.client.impl.EngineClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.OrderingConfig;
import org.camunda.bpm.client.topic.impl.dto.FetchAndLockRequestDto;
import org.camunda.bpm.client.topic.impl.dto.TopicRequestDto;
import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryBuilder;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;

import java.util.List;
import java.util.Map;

public class LocalEngineClient extends EngineClient
{
    private final ProcessEngine processEngine;
    private final ExternalTaskService externalTaskService;
    private final RuntimeService runtimeService;
    public LocalEngineClient(ProcessEngine processEngine,String workerId, int maxTasks,  boolean usePriority) {
        super(workerId, maxTasks, (Long)null,(String)null,null,usePriority, OrderingConfig.empty());
        this.processEngine = processEngine;
        this.externalTaskService = this.processEngine.getExternalTaskService();
        this.runtimeService = this.processEngine.getRuntimeService();
    }

    @Override
    public List<ExternalTask> fetchAndLock(List<TopicRequestDto> topics) {


        FetchAndLockRequestDto payload = new FetchAndLockRequestDto(workerId, maxTasks, asyncResponseTimeout, topics, usePriority);
        ExternalTaskQueryBuilder fetchBuilder = DtoUtils.buildQuery(externalTaskService,payload);
        List<LockedExternalTask> externalTasks = fetchBuilder.execute();
        List<ExternalTask> lockedExternalTaskDtos =
                externalTasks.stream().map(d -> {

                   return (ExternalTask)DtoUtils.fromLockedExternalTask(d);
                }).toList();


        return lockedExternalTaskDtos;

    }

    @Override
    public void lock(String taskId, long lockDuration) {
        this.externalTaskService.lock(taskId, this.getWorkerId(), lockDuration);
    }

    @Override
    public void unlock(String taskId) {
        this.externalTaskService.unlock(taskId);
    }

    @Override
    public void complete(String taskId, Map<String, Object> variables, Map<String, Object> localVariables) {

        this.externalTaskService.complete(taskId, this.getWorkerId(), variables, localVariables);
    }

    @Override
    public void setVariables(String processId, Map<String, Object> variables) {
        this.runtimeService.setVariables(processId,variables);
    }

    @Override
    public void failure(String taskId, String errorMessage, String errorDetails, int retries, long retryTimeout, Map<String, Object> variables, Map<String, Object> localVariables) {
        this.externalTaskService.handleFailure(taskId, this.getWorkerId(), errorMessage, errorDetails, retries, retryTimeout, variables, localVariables);
    }

    @Override
    public void bpmnError(String taskId, String errorCode, String errorMessage, Map<String, Object> variables) {
       this.externalTaskService.handleBpmnError(taskId, this.getWorkerId(), errorCode, errorMessage, variables);
    }

    @Override
    public void extendLock(String taskId, long newDuration) {
        this.externalTaskService.extendLock(taskId, this.getWorkerId(), newDuration);
    }

    @Override
    public byte[] getLocalBinaryVariable(String variableName, String processInstanceId) {
        Object value = this.runtimeService.getVariableTyped(processInstanceId, variableName, true).getValue();
        if( value instanceof byte[] ) {
            return (byte[]) value;
        }
        return null;
    }

}
