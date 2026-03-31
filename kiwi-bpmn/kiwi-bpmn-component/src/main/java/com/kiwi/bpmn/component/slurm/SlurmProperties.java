package com.kiwi.bpmn.component.slurm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "kiwi.bpm.slurm")
@Data
public class SlurmProperties {

    private String workDirectory;
    private int threadPoolSize = 5;

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
