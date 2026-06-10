package com.kiwi.bpmn.component.slurm;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 扫描 Slurm 工作目录，按保留策略删除过期临时文件（基于最后修改时间）。
 */
@Slf4j
public final class SlurmWorkdirCleanup {

    static final List<String> DEFAULT_SUFFIXES = List.of(".sbatch", ".out", ".err", ".flag.done", ".flag");

    private SlurmWorkdirCleanup() {
    }

    /**
     * @return 成功删除的文件数量
     */
    public static int run(File workDir, SlurmProperties.Cleanup cfg) {
        if (workDir == null || !workDir.isDirectory()) {
            log.warn("Slurm cleanup skipped: work directory missing or not a directory: {}", workDir);
            return 0;
        }
        List<String> suffixes = cfg.effectiveSuffixes();
        long now = System.currentTimeMillis();
        int deleted = 0;
        int failed = 0;
        List<File> candidates = collectCandidates(workDir, cfg.isRecursive());
        for (File file : candidates) {
            if (!file.isFile()) {
                continue;
            }
            if (!matchesSuffix(file.getName(), suffixes)) {
                continue;
            }
            long age = now - file.lastModified();
            if (age < cfg.getMinAgeMs()) {
                continue;
            }
            if (age < cfg.getRetentionMs()) {
                continue;
            }
            try {
                Files.delete(file.toPath());
                deleted++;
            } catch (IOException e) {
                failed++;
                log.warn("Slurm cleanup failed to delete file: {} — {}", file.getAbsolutePath(), e.getMessage());
            }
        }
        if (deleted > 0 || failed > 0) {
            log.info("Slurm workdir cleanup finished: deleted={}, failed={}, scanned={}, dir={}",
                    deleted, failed, candidates.size(), workDir.getAbsolutePath());
        }
        return deleted;
    }

    private static List<File> collectCandidates(File root, boolean recursive) {
        List<File> out = new ArrayList<>();
        File[] children = root.listFiles();
        if (children == null) {
            return out;
        }
        for (File child : children) {
            if (child.isFile()) {
                out.add(child);
            } else if (recursive && child.isDirectory()) {
                out.addAll(FileUtils.listFiles(child, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE));
            }
        }
        return out;
    }

    static boolean matchesSuffix(String fileName, List<String> suffixes) {
        List<String> ordered = new ArrayList<>(suffixes);
        ordered.sort(Comparator.comparingInt(String::length).reversed());
        for (String s : ordered) {
            if (fileName.endsWith(s)) {
                return true;
            }
        }
        return false;
    }
}
