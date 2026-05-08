package com.kiwi.bpmn.component.slurm;

import lombok.Data;

@Data
public class SlurmJobResult
{
    /** Slurm / sacct 侧命令退出码；终态上报成功后由 {@link SlurmJobCompleteProcessor} 写入。 */
    private Integer exitCode;

    /** 与终态失败上报一致的人类可读说明；成功且退出码为 0 时可为空。 */
    private String errorMessage;

    /** Slurm {@code sacct} 作业状态字符串（如 {@code COMPLETED}、{@code FAILED}）。 */
    private String slurmState;
}
