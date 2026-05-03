package com.kiwi.bpmn.core.retry;

import org.camunda.bpm.engine.impl.cfg.TransactionContext;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import org.camunda.bpm.engine.impl.jobexecutor.MessageAddedNotification;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.util.ExceptionUtil;

/**
 * 将失败 Job 的 retries 直接降为 0（触发 failed-job incident），不经过
 * {@link org.camunda.bpm.engine.impl.cmd.DefaultJobRetryCmd} 的按周期重试。
 */
final class ExhaustJobRetriesCommand implements Command<Object> {

    private final String jobId;
    private final Throwable exception;

    ExhaustJobRetriesCommand(String jobId, Throwable exception) {
        this.jobId = jobId;
        this.exception = exception;
    }

    @Override
    public Object execute(CommandContext commandContext) {
        JobEntity job = commandContext.getJobManager().findJobById(jobId);
        if (job == null) {
            return null;
        }
        job.unlock();
        if (exception != null) {
            job.setExceptionMessage(exception.getMessage());
            job.setExceptionStacktrace(ExceptionUtil.getExceptionStacktrace(exception));
        }
        job.setRetries(0);
        notifyAcquisition(commandContext);
        return null;
    }

    private static void notifyAcquisition(CommandContext commandContext) {
        JobExecutor jobExecutor = Context.getProcessEngineConfiguration().getJobExecutor();
        MessageAddedNotification messageAddedNotification = new MessageAddedNotification(jobExecutor);
        TransactionContext transactionContext = commandContext.getTransactionContext();
        transactionContext.addTransactionListener(TransactionState.COMMITTED, messageAddedNotification);
    }
}
