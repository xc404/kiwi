package com.kiwi.bpmn.external;

/**
 * {@link AbstractExternalTaskHandler#executeAsync} 完成后的处置方式：结束 External Task（complete）或仅回写变量。
 */
public record ExternalTaskAsyncResult(boolean finishExternalTask) {

    /**
     * 调用 {@link org.camunda.bpm.client.task.ExternalTaskService#complete}，流程离开当前活动；
     * 变量取自 {@link ExternalTaskExecution#getOutputVariable()}。
     */
    public static ExternalTaskAsyncResult completeTask() {
        return new ExternalTaskAsyncResult(true);
    }

    /**
     * 仅 {@link org.camunda.bpm.client.task.ExternalTaskService#setVariables}，活动仍为等待外部任务状态。
     */
    public static ExternalTaskAsyncResult updateVariablesOnly() {
        return new ExternalTaskAsyncResult(false);
    }
}
