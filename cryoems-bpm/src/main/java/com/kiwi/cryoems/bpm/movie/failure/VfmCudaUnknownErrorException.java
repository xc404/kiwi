package com.kiwi.cryoems.bpm.movie.failure;

import com.kiwi.bpmn.core.retry.IRetry;

/**
 * VFM ({@code vfm.sh}) 在 Slurm 节点上初始化 CUDA 时遇到 "CUDA unknown error" 而被迫将可用设备数置零时抛出
 * （stderr 含 {@code CUDA unknown error - this may be due to an incorrectly set up environment, e.g. changing
 * env variable CUDA_VISIBLE_DEVICES after program start. Setting the available devices to be zero}）。
 * <p>
 * 该错误属于"瞬时基础设施/环境问题"——CUDA 驱动状态异常、{@code CUDA_VISIBLE_DEVICES} 被中途变更等——
 * 与业务输入无关；通过实现 {@link IRetry#decreaseRetries()} 返回 {@code false}，让
 * {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner} 在重排时保留当前 {@code retries}，
 * 仅按非递减分支的退避周期重排。
 */
public class VfmCudaUnknownErrorException extends RuntimeException implements IRetry {

    private static final long serialVersionUID = 1L;

    public VfmCudaUnknownErrorException(String message) {
        super(message);
    }

    @Override
    public boolean decreaseRetries() {
        return false;
    }
}
