/**
 * Movie 流水线 Slurm 作业失败解析与瞬时错误识别。
 *
 * <p>实现 {@link com.kiwi.bpmn.component.slurm.SlurmExternalTaskFailureResolver}，
 * 按 {@code taskType}（如 {@code mc2.sh} / {@code vfm.sh}）匹配 stderr 中的标志性错误，
 * 把"基础设施/瞬时"错误（GPU 设备未检测到、CUDA unknown error 等）转换为
 * 实现 {@link com.kiwi.bpmn.core.retry.IRetry} 且 {@code decreaseRetries()} 返回 {@code false} 的异常，
 * 由 {@link com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner} 走非递减重试分支：
 * 保留当前 {@code retries}，仅按非递减 cycle 退避重排。</p>
 */
package com.kiwi.cryoems.bpm.movie.failure;
