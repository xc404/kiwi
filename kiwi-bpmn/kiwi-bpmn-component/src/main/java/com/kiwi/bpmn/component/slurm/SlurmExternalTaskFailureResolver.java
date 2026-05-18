package com.kiwi.bpmn.component.slurm;

import java.util.Map;

/**
 * ? External Task {@code topic} ??? Slurm ???????? {@link SlurmResult}?stderr ??????????
 * ? {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner#plan} ??????
 * <p>
 * ??????? Spring Bean?{@link SlurmTaskManager} ? {@link #topic()} ?????? {@code topicName} ??????
 */
public interface SlurmExternalTaskFailureResolver
{

    /**
     * ? {@link org.camunda.bpm.engine.externaltask.ExternalTask#getTopicName()} ???
     */
    String topic();

    /**
     * @param errorFileContent 已读入的文件文本，与路径对应；读失败或无路径时为 null
     */
    Exception resolve(
            SlurmResult result,
            String errorFileContent,
            Map<String, Object> contextVariables);
}
