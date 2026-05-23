package com.kiwi.cryoems.bpm.movie.failure;

import com.kiwi.bpmn.component.slurm.DefaultSlurmExternalTaskFailureResolver;
import com.kiwi.bpmn.component.slurm.SlurmExternalTaskFailureResolver;
import com.kiwi.bpmn.component.slurm.SlurmJob;
import com.kiwi.bpmn.component.slurm.SlurmJobCompleteProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link SlurmJob#getTaskType()} 为 {@value #TASK_TYPE} 时的失败解析器：
 * 识别 MotionCor2 在节点上未检测到可用 GPU 的瞬时错误（stderr 含
 * {@value #NO_VALID_GPU_DEVICE_MARKER}），返回 {@link MotionCor2NoValidGpuDeviceException}
 * 以触发非递减重试；不匹配时返回 {@code null}，由
 * {@link SlurmJobCompleteProcessor} 回退到 {@link DefaultSlurmExternalTaskFailureResolver}。
 */
@Component
public class MotionCor2SlurmExternalTaskFailureResolver implements SlurmExternalTaskFailureResolver {

    /** 与 {@code taskType} 流程变量及 {@code mc2.sh} 命令首词一致。 */
    static final String TASK_TYPE = "mc2.sh";

    /**
     * MotionCor2 在初始化阶段调用 {@code mCheckGPUs} 时输出的标志性错误：
     * 节点无可用 GPU（驱动异常 / 被独占 / 落到无 GPU 节点等）。
     */
    static final String NO_VALID_GPU_DEVICE_MARKER = "mCheckGPUs: no valid device detected.";

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Exception resolve(
            SlurmJob result, String errorFileContent, Map<String, Object> contextVariables) {
        if (errorFileContent != null && errorFileContent.contains(NO_VALID_GPU_DEVICE_MARKER)) {
            String jobName = result != null && result.getJobName() != null ? result.getJobName() : "";
            return new MotionCor2NoValidGpuDeviceException(
                    "MotionCor2 detected no valid GPU device on the assigned node (jobName="
                            + jobName
                            + "); transient infrastructure issue — retries kept unchanged.");
        }
        return null;
    }
}
