package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.core.retry.IRetry;

/**
 * 应用层并发闸门拒绝提交时抛出。
 * <p>
 * 由 {@link SlurmExternalTaskHandler} 在 {@code sbatch} 之前根据 Mongo {@code slurm_job{status:Running}}
 * 计数与 {@link SlurmProperties#getMaxConcurrentJobs()} 比较得出；抛出后由
 * {@code AbstractExternalTaskHandler} 的统一失败路径走 {@code handleFailure}，
 * 并由 {@code ExternalTaskRetryPlanner} 通过 {@link IRetry#decreaseRetries()} 识别为非递减重试：
 * 保留当前 {@code retries}，按重试周期重排。
 */
public class SlurmOverloadedException extends RuntimeException implements IRetry {

    private static final long serialVersionUID = 1L;

    public SlurmOverloadedException(String message) {
        super(message);
    }

    /**
     * 过载属于"瞬时资源拥塞"，与业务失败无关——保留 {@code retries}，避免短时间拥塞耗尽业务重试预算。
     */
    @Override
    public boolean decreaseRetries() {
        return false;
    }
}
