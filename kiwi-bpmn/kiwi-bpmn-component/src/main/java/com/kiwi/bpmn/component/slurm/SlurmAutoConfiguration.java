package com.kiwi.bpmn.component.slurm;

import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(prefix = "kiwi.bpm.slurm", name = "workDirectory")
@EnableConfigurationProperties({SlurmProperties.class})
@EnableScheduling
@Slf4j
public class SlurmAutoConfiguration {


    @Bean
    public static SlurmService slurmService(SlurmProperties slurmProperties) {
        return new SlurmService(slurmProperties);
    }

    @Bean
    public static SlurmTaskManager slurmTaskManager(SlurmProperties slurmProperties, SlurmService slurmService,
            ProcessEngine processEngine) {
        return new SlurmTaskManager(slurmProperties, slurmService, processEngine);
    }
}