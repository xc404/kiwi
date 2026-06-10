package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.component.activity.ShellActivityBehavior;
import com.kiwi.bpmn.core.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.external.AbstractExternalTaskHandler;
import com.kiwi.bpmn.external.ExternalTaskAsyncResult;
import com.kiwi.bpmn.external.ExternalTaskExecution;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

/**
 * Slurm 主题（{@code topicName = "slurm"}）的 External Task 处理器：根据流程变量生成 sbatch 配置并提交作业，
 * 或在没有启用 Slurm 集成时退化为本地 {@link ShellActivityBehavior} 执行。
 * <p>
 * 仅当 Spring 容器中注册了 {@link SlurmTaskManager} 时走 Slurm 路径；否则 {@link SlurmService} 可为空，
 * 此时 {@link SlurmSbatchConfigBuilder} 仍会被构造，但 {@link #supportSlurm()} 为 false，不会提交 sbatch。
 */
@Slf4j
@Component
@ExternalTaskSubscription(topicName = "slurm", lockDuration = SlurmService.SLURM_TOPIC_LOCK_DURATION_MS)
@ComponentDescription(
    name = "Slurm External Task Handler",
    description = "Handles external tasks related to Slurm job submission and monitoring.",
    version = "1.0",
    group = "脚本",
    inputs = {
        @ComponentParameter(
            key = "command",
            name = "command",
            type = "String",
            description = "The command to be executed in the Slurm job.",
            important = true
        ),
        @ComponentParameter(
            key = "taskType",
            name = "Task Type",
            type = "String",
            description = "Logical task type for failure handling (e.g. SlurmExternalTaskFailureResolver). "
                    + "If omitted, the first whitespace-separated token of command is used."
        ),
        @ComponentParameter(
            key = "slurm_begin",
            name = "Slurm Begin Time",
            type = "String",
            description = "The time when the Slurm job should begin execution."
        ),
        @ComponentParameter(
            key = "slurm_constraints",
            name = "Slurm Constraints",
            type = "String",
            description = "The constraints for the Slurm job (e.g., specific nodes or features)."
        ),
        @ComponentParameter(
            key = "slurm_cpu_per_task",
            name = "Slurm CPU per Task",
            type = "String",
            description = "Number of CPUs per task."
        ),
        @ComponentParameter(
            key = "slurm_error_file",
            name = "Slurm Error File",
            type = "String",
            description = "Path to the error file."
        ),
        @ComponentParameter(
            key = "slurm_exclude",
            name = "Slurm Exclude",
            type = "String",
            description = "Nodes to exclude from the job."
        ),
        @ComponentParameter(
            key = "slurm_dependency",
            name = "Slurm Dependency",
            type = "String",
            description = "Job dependencies."
        ),
        @ComponentParameter(
            key = "slurm_exclusive",
            name = "Slurm Exclusive",
            type = "String",
            description = "Exclusive node allocation."
        ),
        @ComponentParameter(
            key = "slurm_gres",
            name = "Slurm GRES",
            type = "String",
            description = "Generic resources."
        ),
        @ComponentParameter(
            key = "slurm_job_name",
            name = "Slurm Job Name",
            type = "String",
            description = "Name of the Slurm job.",
            important = true
        ),
        @ComponentParameter(
            key = "slurm_label",
            name = "Slurm Label",
            type = "String",
            description = "Label for the job."
        ),
        @ComponentParameter(
            key = "slurm_mem",
            name = "Slurm Memory",
            type = "String",
            description = "Total memory required."
        ),
        @ComponentParameter(
            key = "slurm_mem_per_cpu",
            name = "Slurm Memory per CPU",
            type = "String",
            description = "Memory per CPU."
        ),
        @ComponentParameter(
            key = "slurm_min_nodes",
            name = "Slurm Min Nodes",
            type = "Integer",
            description = "Minimum number of nodes."
        ),
        @ComponentParameter(
            key = "slurm_max_nodes",
            name = "Slurm Max Nodes",
            type = "Integer",
            description = "Maximum number of nodes."
        ),
        @ComponentParameter(
            key = "slurm_task_num",
            name = "Slurm Task Number",
            type = "Integer",
            description = "Number of tasks."
        ),
        @ComponentParameter(
            key = "slurm_nodelist",
            name = "Slurm Nodelist",
            type = "String",
            description = "List of nodes."
        ),
        @ComponentParameter(
            key = "slurm_output_file",
            name = "Slurm Output File",
            type = "String",
            description = "Path to the output file.",
            important = true
        ),
        @ComponentParameter(
            key = "slurm_partition",
            name = "Slurm Partition",
            type = "String",
            description = "Partition to submit the job. If omitted, uses kiwi.bpm.slurm.partition when configured."
        ),
        @ComponentParameter(
            key = "slurm_qos",
            name = "Slurm QoS",
            type = "String",
            description = "Quality of Service."
        ),
        @ComponentParameter(
            key = "slurm_signal",
            name = "Slurm Signal",
            type = "String",
            description = "Signal to send."
        ),
        @ComponentParameter(
            key = "slurm_time",
            name = "Slurm Time",
            type = "String",
            description = "Time limit for the job.",
            important = true
        ),
        @ComponentParameter(
            key = "task_max_time",
            name = "Task Max Time (seconds)",
            type = "Integer",
            description = "Maximum external task lock time in seconds. If set, lock will be extended after successful Slurm submission."
        ),
        @ComponentParameter(
            key = "slurm_account",
            name = "Slurm Account",
            type = "String",
            description = "Account to charge."
        ),
        @ComponentParameter(
            key = "slurm_comment",
            name = "Slurm Comment",
            type = "String",
            description = "Comment for the job."
        ),
        @ComponentParameter(
            key = "slurm_cpus_per_gpu",
            name = "Slurm CPUs per GPU",
            type = "Integer",
            description = "CPUs per GPU."
        ),
        @ComponentParameter(
            key = "slurm_deadline",
            name = "Slurm Deadline",
            type = "String",
            description = "Deadline for the job."
        ),
        @ComponentParameter(
            key = "slurm_chdir",
            name = "Slurm Chdir",
            type = "String",
            description = "Working directory."
        ),
        @ComponentParameter(
            key = "slurm_gpus",
            name = "Slurm GPUs",
            type = "String",
            description = "GPUs required."
        ),
        @ComponentParameter(
            key = "slurm_gpus_per_node",
            name = "Slurm GPUs per Node",
            type = "Integer",
            description = "GPUs per node."
        ),
        @ComponentParameter(
            key = "slurm_gpus_per_task",
            name = "Slurm GPUs per Task",
            type = "Integer",
            description = "GPUs per task."
        )
    },
    outputs = {
        @ComponentParameter(
            key = "slurmJobId",
            name = "Slurm Job ID",
            type = "String",
            description = "Successfully submitted Slurm batch job ID (from sbatch stdout).",
            schema = @Schema(defaultValue = "slurmJobId")
        ),
        @ComponentParameter(
            key = "slurmJobName",
            name = "Slurm Job Name",
            type = "String",
            description = "Resolved job name (--job-name), same as SBATCH job name.",
            schema = @Schema(defaultValue = "slurmJobName")
        ),
        @ComponentParameter(
            key = "sbatchFilePath",
            name = "Sbatch Script Path",
            type = "String",
            description = "Absolute path of the generated .sbatch file under the Slurm work directory.",
            schema = @Schema(defaultValue = "sbatchFilePath")
        ),
        @ComponentParameter(
            key = "outputFilePath",
            name = "Stdout Log Path",
            type = "String",
            description = "Resolved Slurm stdout file path (--output).",
            schema = @Schema(defaultValue = "outputFilePath")
        ),
        @ComponentParameter(
            key = "errorFilePath",
            name = "Stderr Log Path",
            type = "String",
            description = "Resolved Slurm stderr file path (--error).",
            schema = @Schema(defaultValue = "errorFilePath")
        )
    }
)
public class SlurmExternalTaskHandler extends AbstractExternalTaskHandler {

    private final ShellActivityBehavior shellActivityBehavior;
    private final SlurmTaskManager slurmTaskManager;
    private final SlurmSbatchConfigBuilder sbatchConfigBuilder;
    private final SlurmService slurmService;
    private final SlurmProperties slurmProperties;
    private final SlurmJobRepository slurmJobRepository;
    /**
     * @param shellActivityBehavior 无 Slurm 集成时的回退执行器
     * @param slurmService          可选；用于路径解析等，与 {@link SlurmTaskManager} 独立注入
     * @param slurmTaskManager      可选；缺失时表示未装配 Slurm 提交能力，本处理器仅走 Shell 回退
     * @param slurmProperties       可选；提供应用层并发闸门阈值（{@link SlurmProperties#getMaxConcurrentJobs()}）；
     *                              与 {@link SlurmTaskManager} 同源装配，二者通常同时存在
     * @param slurmJobRepository    可选；用于并发闸门按 {@code status=Running} 统计当前在跑作业数；
     *                              缺失或阈值 {@code <= 0} 时跳过闸门
     */
    public SlurmExternalTaskHandler(ShellActivityBehavior shellActivityBehavior,
                                    @Autowired(required = false) SlurmService slurmService,
                                    @Autowired(required = false) SlurmTaskManager slurmTaskManager,
                                    @Autowired(required = false) SlurmProperties slurmProperties,
                                    @Autowired(required = false) SlurmJobRepository slurmJobRepository) {
        this.shellActivityBehavior = shellActivityBehavior;
        this.slurmTaskManager = slurmTaskManager;
        this.sbatchConfigBuilder = new SlurmSbatchConfigBuilder(slurmService);
        this.slurmService = slurmService;
        this.slurmProperties = slurmProperties;
        this.slurmJobRepository = slurmJobRepository;
    }

    /**
     * 异步执行：有 {@link SlurmTaskManager} 则构建 {@link SbatchConfig} 并提交；否则同步跑 Shell 并标记 {@code slurmJobId = skipped}。
     * 必填变量 {@code command} 等在 {@link SlurmSbatchConfigBuilder#build} 中校验并写入 {@link SbatchConfig}。
     */
    @Override
    public CompletableFuture<ExternalTaskAsyncResult> executeAsync(DelegateExecution execution) throws Exception {
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        if (!supportSlurm()) {
            log.warn(
                    "SlurmTaskManager 未装配，Slurm 外部任务回退为 Shell：processInstanceId={}, activityId={}, businessKey={}",
                    processInstanceId,
                    activityId,
                    execution.getBusinessKey());
            shellActivityBehavior.execute(execution);
            execution.setVariable("slurmJobId", "skipped");
            return CompletableFuture.completedFuture(ExternalTaskAsyncResult.updateVariablesOnly());
        }

        String externalTaskId = null;
        String workerId = null;
        if (execution instanceof ExternalTaskExecution ext) {
            externalTaskId = ext.getExternalTask().getId();
            workerId = ext.getExternalTask().getWorkerId();
        }
        enforceConcurrencyGate(execution, externalTaskId);

        SbatchConfig sbatchConfig = sbatchConfigBuilder.build(execution);
        String taskType =
                ExecutionUtils.getStringInputVariable(execution, "taskType")
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .orElseGet(() -> firstCommandToken(sbatchConfig.getCommand()));
        log.info(
                "提交 Slurm 作业：processInstanceId={}, activityId={}, externalTaskId={}, jobName={}, partition={}, taskType={}, command={}",
                processInstanceId,
                activityId,
                externalTaskId,
                sbatchConfig.getJobName(),
                sbatchConfig.getPartition(),
                taskType,
                sbatchConfig.getCommand());
        return this.slurmTaskManager
                .submitSlurmJob(taskType, execution, sbatchConfig, externalTaskId, workerId)
                .thenApply(slurmJob -> {
            log.info(
                    "Slurm 作业已提交：processInstanceId={}, activityId={}, slurmJobId={}",
                    processInstanceId,
                    activityId,
                    slurmJob.getJobId());
            extendLockIfConfigured(execution);
            execution.setVariable("slurmJobId", slurmJob.getJobId());
            execution.setVariable("slurmJobName", slurmJob.getJobName());
            execution.setVariable("sbatchFilePath", slurmJob.getSbatchFilePath());
            execution.setVariable("outputFilePath", slurmJob.getOutputFilePath());
            execution.setVariable("errorFilePath", slurmJob.getErrorFilePath());
            return ExternalTaskAsyncResult.updateVariablesOnly();
        });
    }

    private void extendLockIfConfigured(DelegateExecution execution) {
        if (!(execution instanceof ExternalTaskExecution externalTaskExecution)) {
            return;
        }
        long lockDurationMs = this.slurmService.getExternalTaskLockExtensionDurationMs(execution);
        if (lockDurationMs <= 0) {
            return;
        }
        externalTaskExecution.extendLock(lockDurationMs);
        log.info(
                "ExternalTask lock 已延长：processInstanceId={}, activityId={}, durationMs={}",
                execution.getProcessInstanceId(),
                execution.getCurrentActivityId(),
                lockDurationMs);
    }



    /**
     * 应用层并发闸门：在调用 {@code sbatch} 之前比对 Mongo {@code slurm_job{status:Running}} 的条数与
     * {@link SlurmProperties#getMaxConcurrentJobs()}。达到或超过阈值时抛 {@link SlurmOverloadedException}，
     * 由统一失败路径走 {@code handleFailure} 并按独立退避周期重排。
     * <p>
     * 短路条件：未注入 properties / repository、阈值 {@code <= 0}、或计数查询本身异常时均放行
     * （拒绝提交比放行提交风险更高；计数异常需通过其他通道告警，而非堵在主链路上）。
     */
    private void enforceConcurrencyGate(DelegateExecution execution, String externalTaskId) {
        if (slurmProperties == null || slurmJobRepository == null) {
            return;
        }
        int max = slurmProperties.getMaxConcurrentJobs();
        if (max <= 0) {
            return;
        }
        long running;
        try {
            running = slurmJobRepository.countByStatus(SlurmJobStatus.Running);
        } catch (Exception ex) {
            log.warn("Slurm overload gate count failed, bypass: error={}", ex.toString());
            return;
        }
        if (running < max) {
            return;
        }
        String businessKey = execution.getBusinessKey();
        String processInstanceId = execution.getProcessInstanceId();
        String activityId = execution.getCurrentActivityId();
        log.warn(
                "Slurm overloaded, refusing sbatch: runningCount={}, maxConcurrentJobs={}, processInstanceId={}, activityId={}, externalTaskId={}, businessKey={}",
                running,
                max,
                processInstanceId,
                activityId,
                externalTaskId,
                businessKey);
        throw new SlurmOverloadedException(
                "Slurm overloaded: running=" + running + ", max=" + max);
    }

    /** @return 是否具备 sbatch 提交与监听能力（由 {@link SlurmTaskManager} Bean 是否存在决定） */
    private boolean supportSlurm() {
        return this.slurmTaskManager != null;
    }

    /** 与未显式设置 {@code taskType} 时的默认规则一致：{@code command} 按空白分隔的第一个片段。 */
    private String firstCommandToken(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        String[] parts = command.trim().split("\\s+");
        return parts.length > 0 ? parts[0] : "";
    }
}
