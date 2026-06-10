package com.kiwi.bpmn.component.slurm;

import java.util.Map;

/**
 * 在 Slurm 作业非零退出时，按 {@link SlurmJob#getTaskType()} 选择实现，将 stderr 等上下文转为
 * {@link com.kiwi.bpmn.core.retry.JobRetryException} 等异常，供 {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner#plan} 使用。
 * <p>
 * 各实现注册为 Spring Bean；{@link #taskType()} 与提交时写入 Mongo 的 {@link SlurmJob#taskType} 一致时参与解析。
 */
public interface SlurmExternalTaskFailureResolver
{

    /**
     * 与 {@link SlurmJob#getTaskType()} 匹配的逻辑类型标识（非 Camunda External Task 的 topic）。
     */
    String taskType();

    /**
     * @param errorFileContent 已读入的文件文本，与路径对应；读失败或无路径时为 null
     */
    Exception resolve(
            SlurmJob result,
            String errorFileContent,
            Map<String, Object> contextVariables);
}
