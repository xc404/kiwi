package com.kiwi.bpmn.component.slurm;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Slurm 总开关 {@code kiwi.bpm.slurm.enabled=true}（默认）时的启动期校验：缺少必需配置或 Mongo 跟踪 Bean 则拒绝启动。
 */
public final class SlurmEnabledConditions {

    private SlurmEnabledConditions() {}

    /**
     * @throws IllegalStateException 当 Slurm 已启用但 {@link #workDirectory} 未配置或 sacct 跟踪 Bean 未装配
     */
    public static void validate(
            SlurmProperties slurmProperties,
            ObjectProvider<SlurmJobTracker> slurmJobTracker,
            ObjectProvider<SlurmJobRepository> slurmJobRepository) {
        if (slurmProperties == null || !slurmProperties.isEnabled()) {
            return;
        }
        String workDirectory = slurmProperties.getWorkDirectory();
        if (StringUtils.isBlank(workDirectory)) {
            throw new IllegalStateException(
                    "Slurm is enabled (kiwi.bpm.slurm.enabled=true) but kiwi.bpm.slurm.work-directory is not configured. "
                            + "Set work-directory to a writable path, or disable Slurm with kiwi.bpm.slurm.enabled=false.");
        }
        if (slurmJobTracker.getIfAvailable() == null || slurmJobRepository.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "Slurm is enabled (kiwi.bpm.slurm.enabled=true) but SlurmJob Mongo tracking is not available: "
                            + "configure spring.data.mongodb (MongoTemplate) so SlurmJobRepository is registered, "
                            + "or disable Slurm with kiwi.bpm.slurm.enabled=false.");
        }
    }
}
