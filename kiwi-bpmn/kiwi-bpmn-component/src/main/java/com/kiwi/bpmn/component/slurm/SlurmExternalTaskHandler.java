package com.kiwi.bpmn.component.slurm;


import com.kiwi.bpmn.component.activity.ShellActivityBehavior;
import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.external.AbstractExternalTaskHandler;
import com.kiwi.bpmn.external.ExternalTaskExecution;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@ConditionalOnBean(SlurmProperties.class)
@Component
@ExternalTaskSubscription(topicName = "slurm", lockDuration = 300000)
@ComponentDescription(
    name = "Slurm External Task Handler",
    description = "Handles external tasks related to Slurm job submission and monitoring.",
    version = "1.0",
    inputs = {
        @ComponentParameter(
            key = "slurmCmd",
            name = "slurmCmd",
            type = "String",
            description = "The Slurm command to execute (e.g., sbatch, srun)."
        ),
        @ComponentParameter(
            key = "command",
            name = "command",
            type = "String",
            description = "The command to be executed in the Slurm job."
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
            description = "Name of the Slurm job."
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
            description = "Path to the output file."
        ),
        @ComponentParameter(
            key = "slurm_partition",
            name = "Slurm Partition",
            type = "String",
            description = "Partition to submit the job."
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
            description = "Time limit for the job."
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
    }
)
public class SlurmExternalTaskHandler extends AbstractExternalTaskHandler
{

    private final ShellActivityBehavior shellActivityBehavior;
    private final SlurmService  slurmService;
    private final SlurmTaskManager slurmTaskManager;
    private final SlurmTaskWatcher slurmTaskWatcher;

    public SlurmExternalTaskHandler(ShellActivityBehavior shellActivityBehavior, SlurmService slurmService, SlurmTaskManager slurmTaskManager, SlurmTaskWatcher slurmTaskWatcher) {
        this.shellActivityBehavior = shellActivityBehavior;
        this.slurmService = slurmService;
        this.slurmTaskManager = slurmTaskManager;
        this.slurmTaskWatcher = slurmTaskWatcher;
    }


    @Override
    public CompletableFuture<Void> executeAsync(DelegateExecution execution) throws Exception {
        if(!supportSlurm()){
            shellActivityBehavior.execute(execution);
            return CompletableFuture.completedFuture(null);
        }

        String slurmCmd = ExecutionUtils.getStringInputVariable(execution, "slurmCmd").orElseThrow();
        String command = ExecutionUtils.getStringInputVariable(execution, "command").orElseThrow();

        SbatchConfig sbatchConfig = getSbatchConfig(execution);
        if(execution instanceof ExternalTaskExecution  externalTaskExecution){

            command = command + "\n\n"+ completeCmd(externalTaskExecution.getExternalTask().getId(),
                    ((ExternalTaskExecution) execution).getExternalTask().getWorkerId(), sbatchConfig.getJobName());
        }
        SlurmCmd cmd = SlurmCmd.valueOf(slurmCmd);
        String executionId = execution.getId();
        String batchFile = executionId + ".sbatch";
        File sbatchFile = this.slurmService.createSbatchFile(batchFile, sbatchConfig, command);
        execution.setVariable("sbatchFilePath", sbatchFile.getAbsolutePath());
        execution.setVariable("outputFilePath", sbatchConfig.getOutput_file());
        slurmTaskWatcher.start();
        return this.slurmTaskManager.submitSlurmJob(sbatchFile).thenApply(slurmJob -> {
            execution.setVariable("slurmJobId", slurmJob.getJobId());
            return null;
        });
    }


    private String completeCmd(String taskId, String workId, String jobName) throws IOException {
        String content = taskId + "," +workId +"," + jobName;;
        return  "echo " + content + " > " + this.slurmService.getShellFileDir()+ "/$SLURM_JOB_ID.flag";
    }

    private SbatchConfig getSbatchConfig(DelegateExecution execution) {
        String begin = ExecutionUtils.getStringInputVariable(execution, "slurm_begin").orElse(null);
        String constraints = ExecutionUtils.getStringInputVariable(execution, "slurm_constraints").orElse(null);
        String cpu_per_task = ExecutionUtils.getStringInputVariable(execution, "slurm_cpu_per_task").orElse(null);
        String error_file = ExecutionUtils.getStringInputVariable(execution, "slurm_error_file").orElse(null);
        String exclude = ExecutionUtils.getStringInputVariable(execution, "slurm_exclude").orElse(null);
        String dependency = ExecutionUtils.getStringInputVariable(execution, "slurm_dependency").orElse(null);
        String exclusive = ExecutionUtils.getStringInputVariable(execution, "slurm_exclusive").orElse(null);
        String gres = ExecutionUtils.getStringInputVariable(execution, "slurm_gres").orElse(null);
        String job_name = ExecutionUtils.getStringInputVariable(execution, "slurm_job_name").orElse(null);
        String label  = ExecutionUtils.getStringInputVariable(execution, "slurm_label").orElse(null);
        String mem  = ExecutionUtils.getStringInputVariable(execution, "slurm_mem").orElse(null);
        String mem_per_cpu = ExecutionUtils.getStringInputVariable(execution, "slurm_mem_per_cpu").orElse(null);
        Integer min_nodes = ExecutionUtils.getIntInputVariable(execution, "slurm_min_nodes").orElse(null);
        Integer max_nodes = ExecutionUtils.getIntInputVariable(execution, "slurm_max_nodes").orElse(null);
        Integer task_num = ExecutionUtils.getIntInputVariable(execution, "slurm_task_num").orElse(null);
        String nodelist = ExecutionUtils.getStringInputVariable(execution, "slurm_nodelist").orElse(null);
        String output_file = ExecutionUtils.getStringInputVariable(execution, "slurm_output_file").orElse(null);
        String partition = ExecutionUtils.getStringInputVariable(execution, "slurm_partition").orElse(null);
        String qos = ExecutionUtils.getStringInputVariable(execution, "slurm_qos").orElse(null);
        String signal = ExecutionUtils.getStringInputVariable(execution, "slurm_signal").orElse(null);
        String time = ExecutionUtils.getStringInputVariable(execution, "slurm_time").orElse(null);
        String account = ExecutionUtils.getStringInputVariable(execution, "slurm_account").orElse(null);
        String comment = ExecutionUtils.getStringInputVariable(execution, "slurm_comment").orElse(null);
        Integer cpus_per_gpu = ExecutionUtils.getIntInputVariable(execution, "slurm_cpus_per_gpu").orElse(null);
        String deadline = ExecutionUtils.getStringInputVariable(execution, "slurm_deadline").orElse(null);
        String chdir = ExecutionUtils.getStringInputVariable(execution, "slurm_chdir").orElse(null);
        String gpus = ExecutionUtils.getStringInputVariable(execution, "slurm_gpus").orElse(null);
        Integer gpus_per_node = ExecutionUtils.getIntInputVariable(execution, "slurm_gpus_per_node").orElse(null);
        Integer gpus_per_task = ExecutionUtils.getIntInputVariable(execution, "slurm_gpus_per_task").orElse(null);

        SbatchConfig sbatchConfig = new SbatchConfig();
        sbatchConfig.setBegin(begin);
        sbatchConfig.setConstraints(constraints);
        sbatchConfig.setCpu_per_task(cpu_per_task);
        sbatchConfig.setError_file(error_file);
        sbatchConfig.setExclude(exclude);
        sbatchConfig.setDependency(dependency);
        sbatchConfig.setExclusive(exclusive);
        sbatchConfig.setGres(gres);
        sbatchConfig.setJobName(job_name);
        sbatchConfig.setLabel(label);
        sbatchConfig.setMem(mem);
        sbatchConfig.setMem_per_cpu(mem_per_cpu);
        sbatchConfig.setMin_nodes(min_nodes);
        sbatchConfig.setMax_nodes(max_nodes);
        sbatchConfig.setTask_num(task_num);
        sbatchConfig.setNodelist(nodelist);
        sbatchConfig.setOutput_file(output_file);
        sbatchConfig.setPartition(partition);
        sbatchConfig.setQos(qos);
        sbatchConfig.setSignal(signal);
        sbatchConfig.setTime(time);
        sbatchConfig.setAccount(account);
        sbatchConfig.setComment(comment);
        sbatchConfig.setCpus_per_gpu(cpus_per_gpu);
        sbatchConfig.setDeadline(deadline);
        sbatchConfig.setChdir(chdir);
        sbatchConfig.setGpus(gpus);
        sbatchConfig.setGpus_per_node(gpus_per_node);
        sbatchConfig.setGpus_per_task(gpus_per_task);
        return sbatchConfig;
    }

    private boolean supportSlurm() {
        return true;
    }
}
