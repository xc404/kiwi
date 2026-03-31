package com.kiwi.bpmn.component.slurm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按配置周期清理 Slurm 工作目录下的过期临时文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kiwi.bpm.slurm.cleanup", name = "enabled", havingValue = "true")
public class SlurmWorkdirCleanupScheduler {

    private final SlurmService slurmService;
    private final SlurmProperties slurmProperties;

    @Scheduled(fixedDelayString = "${kiwi.bpm.slurm.cleanup.fixed-delay-ms:3600000}")
    public void cleanupExpiredFiles() {
        try {
            SlurmWorkdirCleanup.run(slurmService.getShellFileDir(), slurmProperties.getCleanup());
        } catch (RuntimeException e) {
            log.warn("Slurm workdir cleanup run failed: {}", e.getMessage(), e);
        }
    }
}
