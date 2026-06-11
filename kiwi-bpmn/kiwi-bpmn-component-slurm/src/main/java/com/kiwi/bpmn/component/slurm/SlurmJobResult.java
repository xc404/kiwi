package com.kiwi.bpmn.component.slurm;

import lombok.Data;

@Data
public class SlurmJobResult
{
    /**
     * 写入 {@link #slurmState} 时表示：本系统 Mongo/sacct 跟踪窗口已到期（{@link SlurmJob#getExpiration()}），
     * 并非 Slurm {@code sacct} 里作业状态 {@code TIMEOUT}。
     */
    public static final String STATE_TRACKING_EXPIRED = "KIWI_TRACKING_EXPIRED";

    /** Slurm / sacct 侧命令退出码；终态上报成功后由 {@link SlurmJobCompleteProcessor} 写入。 */
    private Integer exitCode;

    /** 与终态失败上报一致的人类可读说明；成功且退出码为 0 时可为空。 */
    private String errorMessage;

    /** Slurm {@code sacct} 作业状态字符串（如 {@code COMPLETED}、{@code FAILED}）。 */
    private String slurmState;
}
