package com.kiwi.bpmn.core.retry;

/**
 * 可重试语义标记接口；{@link JobRetryException} 实现本接口，供 Job 失败分类等场景识别。
 * <p>
 * 通过 {@link #decreaseRetries()} 表达"本次失败是否消耗一次重试配额"：默认 {@code true}（与 Camunda
 * {@code DefaultJobRetryCmd} 行为一致）。实现类可在过载、瞬时资源不足等"非业务失败"场景下覆盖为
 * {@code false}，让规划器（如 External Task 的 {@code ExternalTaskRetryPlanner}）在重排时保留当前
 * {@code retries} 值；这样可以避免短期资源拥塞耗尽业务重试预算。
 */
public interface IRetry {

    /**
     * @return {@code true}（默认）：本次失败应递减 {@code retries}；{@code false}：保留当前 {@code retries}，
     *         仅按重试周期重排（典型用法见
     *         {@code com.kiwi.bpmn.component.slurm.SlurmOverloadedException}）。
     */
    default boolean decreaseRetries() {
        return true;
    }
}
