package com.kiwi.bpmn.component.slurm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "kiwi.bpm.slurm")
@Data
public class SlurmProperties {

    /**
     * {@link SlurmJob#getExpiration()} 所代表的时长与外部任务 topic {@code lockDuration}（毫秒）之间的差值。
     * {@link SlurmService#getSlurmJobMaxDuration} 在未配置 {@code task_max_time} 时用 {@code topicLockDuration - delta} 推导默认时长；
     * 回退至 {@link Sacct#getMaxTrackDurationMs()} 时亦与该差值取较大值作为下限。
     * <p>
     * 配置键：{@code kiwi.bpm.slurm.expiration-external-task-lock-delta-ms}。
     */
    private long expirationExternalTaskLockDeltaMs = 60_000L;

    /**
     * 是否启用 Slurm 集成（sbatch、外部任务监听、sacct/Mongo 跟踪、工作目录清理等）。
     * <p>
     * 为唯一总开关，默认 {@code true}。为 {@code true} 时启动期校验：
     * <ul>
     *   <li>必须配置非空的 {@link #workDirectory}</li>
     *   <li>必须能装配 {@link SlurmJobRepository} / {@link SlurmJobTracker}（需 {@code spring.data.mongodb}）</li>
     * </ul>
     * 不满足则应用启动失败。不需要 Slurm 的环境请显式设为 {@code false}。
     */
    private boolean enabled = true;

    private String workDirectory;
    private int threadPoolSize = 5;

    /**
     * 默认 Slurm 分区名；流程变量 {@code slurm_partition} 未传入或为空时使用。
     * 配置键：{@code kiwi.bpm.slurm.partition}。
     */
    private String partition;

    /**
     * Slurm 作业完成写入 .flag 后，调用 External Task {@code complete} 的最大尝试次数（每次失败后休眠 1 秒）。
     * 达到上限仍失败则抛出异常，避免无限重试。
     */
    private int externalTaskCompleteMaxAttempts = 60;

    /**
     * 是否启用已弃用的工作目录 {@code *.flag} 文件监听（默认关闭；作业终态请使用 {@link Sacct}）。
     */
    private boolean flagListenerEnabled = false;

    /**
     * 工作目录下临时文件（.sbatch、日志、flag 等）的自动清理。
     */
    private Cleanup cleanup = new Cleanup();

    /**
     * 周期性 {@code sacct} 查询作业终态；启用后依赖 Mongo 持久化 {@link SlurmJob}。需配置 {@code spring.data.mongodb} 且存在 {@code MongoTemplate}。
     */
    private Sacct sacct = new Sacct();

    @Data
    public static class Sacct {


        /**
         * 两次 sacct 批量查询之间的间隔（毫秒）。
         */
        private long pollIntervalMs = 15_000L;

        /**
         * 自提交起超过该时长仍未终态则按超时失败上报（毫秒）。
         */
        private long maxTrackDurationMs = 900_000L;

        /**
         * sacct 可执行文件（PATH 内名称或绝对路径）。
         */
        private String executable = "sacct";

        /**
         * 附加在 sacct 命令后的参数（例如集群名等）。
         */
        private List<String> extraArgs = new ArrayList<>();

        /**
         * 单次 sacct 调用最多携带的作业 id 个数，避免命令行过长。
         */
        private int maxJobsPerSacctCall = 80;
    }

    @Data
    public static class Cleanup {

        /**
         * 是否启用定时清理（默认关闭，避免误删）。
         */
        private boolean enabled = false;

        /**
         * 文件保留时长：仅当「距最后修改时间」超过该值才可能删除（毫秒）。
         */
        private long retentionMs = 7L * 24 * 60 * 60 * 1000;

        /**
         * 两次清理任务之间的间隔（毫秒），供 {@code @Scheduled(fixedDelayString)} 使用。
         */
        private long fixedDelayMs = 3600_000L;

        /**
         * 最小年龄：距最后修改时间小于该值的文件不删，降低与正在写入文件的竞态（毫秒）。
         */
        private long minAgeMs = 60_000L;

        /**
         * 是否递归扫描子目录（默认仅工作目录根下文件）。
         */
        private boolean recursive = false;

        /**
         * 可删除文件的后缀白名单；为空时使用内置默认列表。
         */
        private List<String> suffixes = new ArrayList<>();

        public List<String> effectiveSuffixes() {
            if (suffixes == null || suffixes.isEmpty()) {
                return SlurmWorkdirCleanup.DEFAULT_SUFFIXES;
            }
            return suffixes;
        }
    }
}
