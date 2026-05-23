package com.kiwi.cryoems.bpm.movie.failure;

import com.kiwi.bpmn.core.retry.IRetry;

/**
 * MotionCor2 ({@code mc2.sh}) 在被分配到的 Slurm 计算节点上未检测到可用 GPU 时抛出
 * （stderr 含 {@code mCheckGPUs: no valid device detected.}）。
 * <p>
 * 该错误属于"瞬时资源/调度问题"——节点 GPU 驱动异常、独占进程占用、或本次调度落到无 GPU 节点——
 * 与业务输入无关；通过实现 {@link IRetry#decreaseRetries()} 返回 {@code false}，让
 * {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner} 在重排时保留当前 {@code retries}，
 * 仅按非递减分支的退避周期重排，避免短时基础设施抖动耗尽业务重试预算。
 */
public class MotionCor2NoValidGpuDeviceException extends RuntimeException implements IRetry {

    private static final long serialVersionUID = 1L;

    public MotionCor2NoValidGpuDeviceException(String message) {
        super(message);
    }

    @Override
    public boolean decreaseRetries() {
        return false;
    }
}
