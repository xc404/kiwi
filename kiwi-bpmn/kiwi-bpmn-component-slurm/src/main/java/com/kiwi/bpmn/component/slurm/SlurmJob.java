package com.kiwi.bpmn.component.slurm;

import com.kiwi.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Slurm 提交结果；启用 sacct 跟踪时持久化到 Mongo（主键与 {@link #jobId} 一致），供 {@link SlurmJobTracker} 轮询终态。
 * 跟踪起算时间使用 {@link com.kiwi.common.entity.BaseEntity#getCreatedTime()}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "slurm_job")
public class SlurmJob extends BaseEntity<String> implements Cloneable{

    private String jobId;

    /** Camunda 流程实例 id，便于在 Mongo 中按流程检索。 */
    private String processInstanceId;

    /** BPMN 活动 id（如 {@code Activity_095vcnd}）。 */
    private String activityId;

    /** Camunda execution id（与 sbatch 文件名后缀一致）。 */
    private String executionId;
    private String jobName;

    /** 提交的作业命令（如 sbatch 行或实际执行的命令）。 */
    private String command;

    /**
     * 业务任务类型，用于 {@link SlurmExternalTaskFailureResolver} 等按类型解析失败；来自流程变量 {@code taskType}，
     * 未设置时为 {@link #command} 按空白分隔的第一个词。
     */
    private String taskType;

    private String sbatchFilePath;
    private String outputFilePath;
    private String errorFilePath;

    /** Camunda 外部任务 id */
    private String externalTaskId;

    /** 外部任务 workerId */
    private String workerId;

    /**
     * 本系统跟踪状态；新建时为 {@link SlurmJobStatus#Running}，终态上报成功后为 {@link SlurmJobStatus#Completed}。
     * 与 {@link #completeProcessLock} 解耦：上报 Camunda 终态期间 {@code status} 可仍为 {@link SlurmJobStatus#Running}。
     */
    private SlurmJobStatus status;

    /**
     * Slurm {@code sacct} 作业状态字符串（如 {@code COMPLETED}、{@code FAILED}），与 {@link #status} 含义不同；
     * 一般仅在 sacct 判终态后由 {@link SlurmJobTracker} / 终态持久化写入。
     */
    private String slurmState;

    /**
     * 终态上报（complete / handleFailure）的 Mongo 乐观锁：{@code true} 表示本节点已抢到锁、正在上报；
     * 与 {@link #status} 独立，避免用“伪状态”表达锁。
     */
    private Boolean completeProcessLock;

    /** Slurm / sacct 侧命令退出码；终态上报成功后由 {@link SlurmJobCompleteProcessor} 写入。 */
    private Integer exitCode;

    /** 与终态失败上报一致的人类可读说明；成功且退出码为 0 时可为空。 */
    private String errorMessage;

    /**
     * sacct 跟踪视为超时的绝对时刻（通常 ≈ {@link com.kiwi.common.entity.BaseEntity#getCreatedTime()} + 跟踪窗口）。
     * 窗口时长见 {@link SlurmService#getSlurmJobMaxDuration(org.operaton.bpm.engine.delegate.DelegateExecution)}。
     */
    private Date expiration;

    public void setJobId(String jobId) {
        this.jobId = jobId;
        setId(jobId);
    }


    @Override
    public SlurmJob clone() {
        try {
            SlurmJob clone = (SlurmJob) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch( CloneNotSupportedException e ) {
            throw new AssertionError();
        }
    }
}
