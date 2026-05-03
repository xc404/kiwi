package com.kiwi.bpmn.component.slurm;

import com.kiwi.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Slurm 提交结果；启用 sacct 跟踪时持久化到 Mongo（主键与 {@link #jobId} 一致），供 {@link SlurmJobTracker} 轮询终态。
 * 跟踪起算时间使用 {@link com.kiwi.common.entity.BaseEntity#getCreatedTime()}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlurmJob extends BaseEntity<String> {

    private String jobId;
    private String jobName;
    private String sbatchFilePath;
    private String outputFilePath;
    private String errorFilePath;

    /** Camunda 外部任务 id（sacct 终态上报用） */
    private String externalTaskId;

    /** 外部任务 workerId */
    private String workerId;

    /**
     * 本系统跟踪状态；新建时为 {@link SlurmJobStatus#RUNNING}，终态上报成功后为 {@link SlurmJobStatus#TERMINATED}。
     * 与 {@link #terminalReportLocked} 解耦：上报 Camunda 终态期间 {@code status} 可仍为 {@link SlurmJobStatus#RUNNING}。
     */
    private SlurmJobStatus status;

    /**
     * 终态上报（complete / handleFailure）的 Mongo 乐观锁：{@code true} 表示本节点已抢到锁、正在上报；
     * 与 {@link #status} 独立，避免用“伪状态”表达锁。
     */
    private Boolean terminalReportLocked;

    /** Slurm / sacct 侧命令退出码；终态上报成功后由 {@link SlurmJobCompleteProcessor} 写入。 */
    private Integer exitCode;

    /** 与终态失败上报一致的人类可读说明；成功且退出码为 0 时可为空。 */
    private String errorMessage;

    public void setJobId(String jobId) {
        this.jobId = jobId;
        setId(jobId);
    }
}
