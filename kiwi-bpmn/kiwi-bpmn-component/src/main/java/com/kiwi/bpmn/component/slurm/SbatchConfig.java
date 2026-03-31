package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.component.utils.ExecutionUtils;

public class SbatchConfig
{
    private String jobName;
    private String begin;
    private String constraints;
    private String cpu_per_task;
    private String error_file;
    private String exclude;
    private String dependency;
    private String exclusive;
    private String gres;
    private String label;
    private String mem;
    private String mem_per_cpu;
    private Integer min_nodes;
    private Integer max_nodes;
    private Integer task_num;
    private String nodelist;
    private String output_file;
    private String partition;
    private String qos;
    private String signal;
    private String time;
    private String account;
    private String comment;
    private Integer cpus_per_gpu;
    private String deadline;
    private String chdir;
    private String gpus;
    private Integer gpus_per_node;
    private Integer gpus_per_task;


    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getBegin() {
        return begin;
    }

    public void setBegin(String begin) {
        this.begin = begin;
    }

    public String getConstraints() {
        return constraints;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public String getCpu_per_task() {
        return cpu_per_task;
    }

    public void setCpu_per_task(String cpu_per_task) {
        this.cpu_per_task = cpu_per_task;
    }

    public String getError_file() {
        return error_file;
    }

    public void setError_file(String error_file) {
        this.error_file = error_file;
    }

    public String getExclude() {
        return exclude;
    }

    public void setExclude(String exclude) {
        this.exclude = exclude;
    }

    public String getDependency() {
        return dependency;
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    public String getExclusive() {
        return exclusive;
    }

    public void setExclusive(String exclusive) {
        this.exclusive = exclusive;
    }

    public String getGres() {
        return gres;
    }

    public void setGres(String gres) {
        this.gres = gres;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getMem() {
        return mem;
    }

    public void setMem(String mem) {
        this.mem = mem;
    }

    public String getMem_per_cpu() {
        return mem_per_cpu;
    }

    public void setMem_per_cpu(String mem_per_cpu) {
        this.mem_per_cpu = mem_per_cpu;
    }

    public Integer getMin_nodes() {
        return min_nodes;
    }

    public void setMin_nodes(Integer min_nodes) {
        this.min_nodes = min_nodes;
    }

    public Integer getMax_nodes() {
        return max_nodes;
    }

    public void setMax_nodes(Integer max_nodes) {
        this.max_nodes = max_nodes;
    }

    public Integer getTask_num() {
        return task_num;
    }

    public void setTask_num(Integer task_num) {
        this.task_num = task_num;
    }

    public String getNodelist() {
        return nodelist;
    }

    public void setNodelist(String nodelist) {
        this.nodelist = nodelist;
    }

    public String getOutput_file() {
        return output_file;
    }

    public void setOutput_file(String output_file) {
        this.output_file = output_file;
    }

    public String getPartition() {
        return partition;
    }

    public void setPartition(String partition) {
        this.partition = partition;
    }

    public String getQos() {
        return qos;
    }

    public void setQos(String qos) {
        this.qos = qos;
    }

    public String getSignal() {
        return signal;
    }

    public void setSignal(String signal) {
        this.signal = signal;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getCpus_per_gpu() {
        return cpus_per_gpu;
    }

    public void setCpus_per_gpu(Integer cpus_per_gpu) {
        this.cpus_per_gpu = cpus_per_gpu;
    }

    public String getDeadline() {
        return deadline;
    }

    public void setDeadline(String deadline) {
        this.deadline = deadline;
    }

    public String getChdir() {
        return chdir;
    }

    public void setChdir(String chdir) {
        this.chdir = chdir;
    }

    public String getGpus() {
        return gpus;
    }

    public void setGpus(String gpus) {
        this.gpus = gpus;
    }

    public Integer getGpus_per_node() {
        return gpus_per_node;
    }

    public void setGpus_per_node(Integer gpus_per_node) {
        this.gpus_per_node = gpus_per_node;
    }

    public Integer getGpus_per_task() {
        return gpus_per_task;
    }

    public void setGpus_per_task(Integer gpus_per_task) {
        this.gpus_per_task = gpus_per_task;
    }

    public String toSbatchCmd() {
        StringBuilder stringBuilder = new StringBuilder();
        if (jobName != null) {
            stringBuilder.append("#SBATCH --job-name=").append(jobName).append("\n");
        }
        if (begin != null) {
            stringBuilder.append("#SBATCH --begin=").append(begin).append("\n");
        }
        if (constraints != null) {
            stringBuilder.append("#SBATCH --constraints=").append(constraints).append("\n");
        }
        if (cpu_per_task != null) {
            stringBuilder.append("#SBATCH --cpus-per-task=").append(cpu_per_task).append("\n");
        }
        if (error_file != null) {
            stringBuilder.append("#SBATCH --error=").append(error_file).append("\n");
        }
        if (exclude != null) {
            stringBuilder.append("#SBATCH --exclude=").append(exclude).append("\n");
        }
        if (dependency != null) {
            stringBuilder.append("#SBATCH --dependency=").append(dependency).append("\n");
        }
        if (exclusive != null) {
            stringBuilder.append("#SBATCH --exclusive=").append(exclusive).append("\n");
        }
        if (gres != null) {
            stringBuilder.append("#SBATCH --gres=").append(gres).append("\n");
        }
        if (label != null) {
            stringBuilder.append("#SBATCH --label=").append(label).append("\n");
        }
        if (mem != null) {
            stringBuilder.append("#SBATCH --mem=").append(mem).append("\n");
        }
        if (mem_per_cpu != null) {
            stringBuilder.append("#SBATCH --mem-per-cpu=").append(mem_per_cpu).append("\n");
        }
        if (min_nodes != null) {
            stringBuilder.append("#SBATCH --minnodes=").append(min_nodes).append("\n");
        }
        if (max_nodes != null) {
            stringBuilder.append("#SBATCH --maxnodes=").append(max_nodes).append("\n");
        }
        if (task_num != null) {
            stringBuilder.append("#SBATCH --ntasks=").append(task_num).append("\n");
        }
        if (nodelist != null) {
            stringBuilder.append("#SBATCH --nodelist=").append(nodelist).append("\n");
        }
        if (output_file != null) {
            stringBuilder.append("#SBATCH --output=").append(output_file).append("\n");
        }
        if (partition != null) {
            stringBuilder.append("#SBATCH --partition=").append(partition).append("\n");
        }
        if (qos != null) {
            stringBuilder.append("#SBATCH --qos=").append(qos).append("\n");
        }
        if (signal != null) {
            stringBuilder.append("#SBATCH --signal=").append(signal).append("\n");
        }
        if (time != null) {
            stringBuilder.append("#SBATCH --time=").append(time).append("\n");
        }
        if (account != null) {
            stringBuilder.append("#SBATCH --account=").append(account).append("\n");
        }
        if (comment != null) {
            stringBuilder.append("#SBATCH --comment=").append(comment).append("\n");
        }
        if (cpus_per_gpu != null) {
            stringBuilder.append("#SBATCH --cpus-per-gpu=").append(cpus_per_gpu).append("\n");
        }
        if (deadline != null) {
            stringBuilder.append("#SBATCH --deadline=").append(deadline).append("\n");
        }
        if (chdir != null) {
            stringBuilder.append("#SBATCH --chdir=").append(chdir).append("\n");
        }
        if (gpus != null) {
            stringBuilder.append("#SBATCH --gpus=").append(gpus).append("\n");
        }
        if (gpus_per_node != null) {
            stringBuilder.append("#SBATCH --gpus-per-node=").append(gpus_per_node).append("\n");
        }
        if (gpus_per_task != null) {
            stringBuilder.append("#SBATCH --gpus-per-task=").append(gpus_per_task).append("\n");
        }
        return stringBuilder.toString();
    }
}