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
     * 本系统跟踪状态；新建时为 {@link SlurmJobStatus#RUNNING}；上报 Camunda 终态前经 Repository 原子改为
     * {@link SlurmJobStatus#REPORTING_TERMINAL}（乐观锁）；完成后为 {@link SlurmJobStatus#TERMINATED}。
     */
    private SlurmJobStatus status;

    public void setJobId(String jobId) {
        this.jobId = jobId;
        setId(jobId);
    }
}
