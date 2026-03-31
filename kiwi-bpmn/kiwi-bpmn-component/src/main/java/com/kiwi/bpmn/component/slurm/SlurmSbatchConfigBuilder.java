package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.util.Optional;

/**
 * 从流程变量组装 {@link SbatchConfig}（作业名、日志路径默认值及相对路径解析）。
 */
final class SlurmSbatchConfigBuilder {

    private final SlurmService slurmService;

    SlurmSbatchConfigBuilder(SlurmService slurmService) {
        this.slurmService = slurmService;
    }

    SbatchConfig build(DelegateExecution execution) {
        String begin = ExecutionUtils.getStringInputVariable(execution, "slurm_begin").orElse(null);
        String constraints = ExecutionUtils.getStringInputVariable(execution, "slurm_constraints").orElse(null);
        String cpu_per_task = ExecutionUtils.getStringInputVariable(execution, "slurm_cpu_per_task").orElse(null);
        String jobName = nonBlankOrElse(
                ExecutionUtils.getStringInputVariable(execution, "slurm_job_name"),
                defaultSlurmJobName(execution));
        String errorFile = nonBlankOrElse(
                ExecutionUtils.getStringInputVariable(execution, "slurm_error_file"),
                jobName + ".err");
        String exclude = ExecutionUtils.getStringInputVariable(execution, "slurm_exclude").orElse(null);
        String dependency = ExecutionUtils.getStringInputVariable(execution, "slurm_dependency").orElse(null);
        String exclusive = ExecutionUtils.getStringInputVariable(execution, "slurm_exclusive").orElse(null);
        String gres = ExecutionUtils.getStringInputVariable(execution, "slurm_gres").orElse(null);
        String label = ExecutionUtils.getStringInputVariable(execution, "slurm_label").orElse(null);
        String mem = ExecutionUtils.getStringInputVariable(execution, "slurm_mem").orElse(null);
        String mem_per_cpu = ExecutionUtils.getStringInputVariable(execution, "slurm_mem_per_cpu").orElse(null);
        Integer min_nodes = ExecutionUtils.getIntInputVariable(execution, "slurm_min_nodes").orElse(null);
        Integer max_nodes = ExecutionUtils.getIntInputVariable(execution, "slurm_max_nodes").orElse(null);
        Integer task_num = ExecutionUtils.getIntInputVariable(execution, "slurm_task_num").orElse(null);
        String nodelist = ExecutionUtils.getStringInputVariable(execution, "slurm_nodelist").orElse(null);
        String outputFile = nonBlankOrElse(
                ExecutionUtils.getStringInputVariable(execution, "slurm_output_file"),
                jobName + ".out");
        errorFile = slurmService.resolvePathUnderShellDir(errorFile);
        outputFile = slurmService.resolvePathUnderShellDir(outputFile);
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
        sbatchConfig.setError_file(errorFile);
        sbatchConfig.setExclude(exclude);
        sbatchConfig.setDependency(dependency);
        sbatchConfig.setExclusive(exclusive);
        sbatchConfig.setGres(gres);
        sbatchConfig.setJobName(jobName);
        sbatchConfig.setLabel(label);
        sbatchConfig.setMem(mem);
        sbatchConfig.setMem_per_cpu(mem_per_cpu);
        sbatchConfig.setMin_nodes(min_nodes);
        sbatchConfig.setMax_nodes(max_nodes);
        sbatchConfig.setTask_num(task_num);
        sbatchConfig.setNodelist(nodelist);
        sbatchConfig.setOutput_file(outputFile);
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

    private static String nonBlankOrElse(Optional<String> value, String defaultValue) {
        return value.map(String::trim).filter(s -> !s.isEmpty()).orElse(defaultValue);
    }

    /**
     * 默认作业名：当前活动名 + 执行 ID，并做 Slurm job-name 允许的字符与长度裁剪。
     */
    private static String defaultSlurmJobName(DelegateExecution execution) {
        String activityName = execution.getCurrentActivityName();
        String execId = execution.getId();
        String namePart = (activityName != null && !activityName.isBlank()) ? activityName.trim() : "activity";
        String idPart = (execId != null && !execId.isBlank()) ? execId.trim() : "unknown";
        String raw = namePart + "_" + idPart;
        String sanitized = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        return sanitized.isEmpty() ? "kiwi-slurm" : sanitized;
    }
}
