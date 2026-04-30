package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.component.activity.ShellActivityBehavior;
import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.external.AbstractExternalTaskHandler;
import com.kiwi.bpmn.external.ExternalTaskAsyncResult;
import io.swagger.v3.oas.annotations.media.Schema;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ExternalTaskSubscription(topicName = "slurm", lockDuration = 300000)
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
            description = "Partition to submit the job.",
            important = true
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

    public SlurmExternalTaskHandler(ShellActivityBehavior shellActivityBehavior,
                                    @Autowired(required = false) SlurmService slurmService,
                                    @Autowired(required = false) SlurmTaskManager slurmTaskManager) {
        this.shellActivityBehavior = shellActivityBehavior;
        this.slurmTaskManager = slurmTaskManager;
        this.sbatchConfigBuilder = new SlurmSbatchConfigBuilder(slurmService);
    }

    @Override
    public CompletableFuture<ExternalTaskAsyncResult> executeAsync(DelegateExecution execution) throws Exception {
        if (!supportSlurm()) {
            shellActivityBehavior.execute(execution);
            execution.setVariable("slurmJobId", "skipped");
            return CompletableFuture.completedFuture(ExternalTaskAsyncResult.updateVariablesOnly());
        }

        String slurmCmd = ExecutionUtils.getStringInputVariable(execution, "command").orElseThrow(() ->
                new IllegalArgumentException("Missing required input variable: command"));
        SlurmCmd.valueOf(slurmCmd);

        SbatchConfig sbatchConfig = sbatchConfigBuilder.build(execution);
        return this.slurmTaskManager.submitSlurmJob(execution, sbatchConfig).thenApply(slurmJob -> {

            execution.setVariable("slurmJobId", slurmJob.getJobId());
            execution.setVariable("slurmJobName", slurmJob.getJobName());
            execution.setVariable("sbatchFilePath", slurmJob.getSbatchFilePath());
            execution.setVariable("outputFilePath", slurmJob.getOutputFilePath());
            execution.setVariable("errorFilePath", slurmJob.getErrorFilePath());
            return ExternalTaskAsyncResult.updateVariablesOnly();
        });
    }

    private boolean supportSlurm() {
        return this.slurmTaskManager != null;
    }
}
