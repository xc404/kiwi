package com.kiwi.bpmn.component.slurm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "kiwi.bpm.slurm")
@Data
public class SlurmProperties {

    /**
     * 是否启用 Slurm 集成（sbatch、外部任务监听、工作目录清理等）。
     * 为唯一总开关：仅当为 true 且配置了 {@link #workDirectory} 时加载 Slurm 相关 Bean。
     * 若不在配置文件中设置该项，默认为 true，与历史上仅配置 {@code work-directory} 的行为一致；
     * 显式设为 {@code false} 可关闭 Slurm。
     */
    private boolean enabled = true;

    private String workDirectory;
    private int threadPoolSize = 5;

    /**
     * Slurm 作业完成写入 .flag 后，调用 External Task {@code complete} 的最大尝试次数（每次失败后休眠 1 秒）。
     * 达到上限仍失败则抛出异常，避免无限重试。
     */
    private int externalTaskCompleteMaxAttempts = 60;

    /**
     * 与外部任务 client 的 workerId 一致时，本机才处理 {@code *.flag}（多节点共享 Slurm 工作目录时避免误 complete/handleFailure）。
     * 未配置或为空时，SlurmTaskManager 会回退使用 {@code kiwi.bpm.external-task.worker-id}；两者皆空则不做过滤（兼容单机）。
     */
    private String externalTaskWorkerId;

    /**
     * 工作目录下临时文件（.sbatch、日志、flag 等）的自动清理。
     */
    private Cleanup cleanup = new Cleanup();

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
