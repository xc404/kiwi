package com.kiwi.cryoems.bpm.movie.failure;

import com.kiwi.bpmn.component.slurm.DefaultSlurmExternalTaskFailureResolver;
import com.kiwi.bpmn.component.slurm.SlurmExternalTaskFailureResolver;
import com.kiwi.bpmn.component.slurm.SlurmJob;
import com.kiwi.bpmn.component.slurm.SlurmJobCompleteProcessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link SlurmJob#getTaskType()} 为 {@value #TASK_TYPE} 时的失败解析器：
 * 识别 VFM 在 CUDA 初始化阶段出现的 "CUDA unknown error"（stderr 含
 * {@value #CUDA_UNKNOWN_ERROR_MARKER}），返回 {@link VfmCudaUnknownErrorException}
 * 以触发非递减重试；不匹配时返回 {@code null}，由
 * {@link SlurmJobCompleteProcessor} 回退到 {@link DefaultSlurmExternalTaskFailureResolver}。
 */
@Component
public class VfmSlurmExternalTaskFailureResolver implements SlurmExternalTaskFailureResolver {

    /** 与 {@code taskType} 流程变量及 {@code vfm.sh} 命令首词一致。 */
    static final String TASK_TYPE = "vfm.sh";

    /**
     * VFM 在初始化 CUDA 时输出的标志性错误（CUDA_VISIBLE_DEVICES 中途变更、驱动状态异常等导致
     * 程序将可用设备数置零）。
     */
    static final String CUDA_UNKNOWN_ERROR_MARKER =
            "CUDA unknown error - this may be due to an incorrectly set up environment, e.g. changing env variable CUDA_VISIBLE_DEVICES after program start. Setting the available devices to be zero";

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public Exception resolve(
            SlurmJob result, String errorFileContent, Map<String, Object> contextVariables) {
        if (errorFileContent != null && errorFileContent.contains(CUDA_UNKNOWN_ERROR_MARKER)) {
            String jobName = result != null && result.getJobName() != null ? result.getJobName() : "";
            return new VfmCudaUnknownErrorException(
                    "VFM encountered a transient CUDA unknown error on the assigned node (jobName="
                            + jobName
                            + "); transient infrastructure issue — retries kept unchanged.");
        }
        return null;
    }
}
