package com.kiwi.bpmn.component.slurm;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 在 Slurm 自动配置加载后执行 {@link SlurmEnabledConditions#validate}，不满足则阻止应用启动。
 */
public class SlurmEnabledConditionsValidator implements InitializingBean {

    private final SlurmProperties slurmProperties;
    private final ObjectProvider<SlurmJobTracker> slurmJobTracker;
    private final ObjectProvider<SlurmJobRepository> slurmJobRepository;

    public SlurmEnabledConditionsValidator(
            SlurmProperties slurmProperties,
            ObjectProvider<SlurmJobTracker> slurmJobTracker,
            ObjectProvider<SlurmJobRepository> slurmJobRepository) {
        this.slurmProperties = slurmProperties;
        this.slurmJobTracker = slurmJobTracker;
        this.slurmJobRepository = slurmJobRepository;
    }

    @Override
    public void afterPropertiesSet() {
        SlurmEnabledConditions.validate(slurmProperties, slurmJobTracker, slurmJobRepository);
    }
}
