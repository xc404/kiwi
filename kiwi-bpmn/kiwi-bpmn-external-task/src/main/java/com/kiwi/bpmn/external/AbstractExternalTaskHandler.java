package com.kiwi.bpmn.external;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class AbstractExternalTaskHandler implements JavaDelegate, ExternalTaskHandler
{
    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        ExternalTaskExecution externalTaskExecution = new ExternalTaskExecution(externalTask, externalTaskService);
        try {
            Date lockExpirationTime = externalTaskExecution.getLockExpirationTime();
            long duration = lockExpirationTime.getTime() - System.currentTimeMillis();
            this.executeAsync(externalTaskExecution).thenApply(i -> {
                if(externalTaskExecution.isComplete()){
                    externalTaskService.complete(externalTask, externalTaskExecution.getOutputVariable());
                }else {
                    externalTaskService.setVariables(externalTask, externalTaskExecution.getOutputVariable());
                }
                return null;
            }).get(duration, TimeUnit.MILLISECONDS);
        } catch( Exception e ) {
            log.error("Failed to execute external task, topic: {}, id: {}, error: {}", externalTask.getTopicName(), externalTask.getId(), e.getMessage(),e);
            externalTaskService.handleFailure(externalTask.getId(), e.getMessage(), e.getLocalizedMessage(), Optional.ofNullable(externalTask.getRetries()).orElse(0), 0);
        }
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        long duration = 0;
        if( execution instanceof ExternalTaskExecution externalTaskExecution ) {
            Date lockExpirationTime = externalTaskExecution.getLockExpirationTime();
            duration = lockExpirationTime.getTime() - System.currentTimeMillis();
        }
        if( duration <= 0 ) {
            this.executeAsync(execution).get();
        } else {
            this.executeAsync(execution).get(duration, TimeUnit.MILLISECONDS);
        }

    }


    public abstract CompletableFuture<Void> executeAsync(DelegateExecution execution) throws Exception;
}
