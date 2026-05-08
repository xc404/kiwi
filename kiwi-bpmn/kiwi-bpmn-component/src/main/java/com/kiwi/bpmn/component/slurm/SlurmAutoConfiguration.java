package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "kiwi.bpm.slurm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SlurmProperties.class})
@EnableScheduling
@Slf4j
public class SlurmAutoConfiguration {

    @Bean
    public static SlurmService slurmService(SlurmProperties slurmProperties) {
        return new SlurmService(slurmProperties);
    }

    @Bean

    public static DefaultSlurmExternalTaskFailureResolver defaultSlurmExternalTaskFailureResolver() {
        return new DefaultSlurmExternalTaskFailureResolver();
    }

    /**
     * sacct 终态上报与跟踪依赖 {@link SlurmJobRepository}（Mongo）；无该仓储 Bean 时不注册，
     * {@link SlurmTaskManager} 仍可用且通过 {@code ObjectProvider<SlurmJobTracker>} 在无跟踪时跳过落库。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(SlurmJobRepository.class)
    static class SlurmJobMongoTrackingConfiguration {

        @Bean
        public static SlurmJobCompleteProcessor slurmJobCompleteProcessor(
                ProcessEngine processEngine,
                ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner,
                List<SlurmExternalTaskFailureResolver> slurmExternalTaskFailureResolvers,
                DefaultSlurmExternalTaskFailureResolver defaultSlurmExternalTaskFailureResolver,
                SlurmProperties slurmProperties,
                SlurmJobRepository slurmJobRepository) {
            return new SlurmJobCompleteProcessor(
                    processEngine,
                    externalTaskRetryPlanner,
                    slurmExternalTaskFailureResolvers != null ? slurmExternalTaskFailureResolvers : List.of(),
                    defaultSlurmExternalTaskFailureResolver,
                    slurmProperties,
                    slurmJobRepository);
        }

        @Bean
        public SlurmJobTracker slurmJobTracker(
                SlurmProperties slurmProperties,
                SlurmJobCompleteProcessor slurmJobCompleteProcessor,
                SlurmJobRepository slurmJobRepository) {
            return new SlurmJobTracker(slurmProperties, slurmJobCompleteProcessor, slurmJobRepository);
        }
    }

    @Bean
    public static SlurmTaskManager slurmTaskManager(
            SlurmProperties slurmProperties,
            SlurmService slurmService,
            ObjectProvider<SlurmJobTracker> slurmJobTracker) {
        return new SlurmTaskManager(slurmProperties, slurmService, slurmJobTracker);
    }
}
