package com.kiwi.bpmn.component.slurm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "kiwi.bpm.slurm",  name = "slurm-file-path")
@EnableConfigurationProperties({SlurmProperties.class})
@Slf4j
public class SlurmAutoConfiguration implements InitializingBean
{


    @Bean
    public static SlurmService slurmService(SlurmProperties slurmProperties) {
        return new SlurmService(slurmProperties);
    }

    @Bean
    public static SlurmTaskManager slurmTaskManager() {
        return new SlurmTaskManager();
    }

    @Bean
    public static  SlurmTaskWatcher slurmTaskWatcher() {
        return new SlurmTaskWatcher();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("SlurmAutoConfiguration initialized with properties");
    }
}