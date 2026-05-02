package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.external.config.ClientProperties;
import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
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
    public static SlurmJobCompleteProcessor slurmJobCompleteProcessor(
            ProcessEngine processEngine,
            ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner,
            List<SlurmExternalTaskFailureResolver> slurmExternalTaskFailureResolvers,
            SlurmProperties slurmProperties,
            ObjectProvider<SlurmJobRepository> slurmJobRepository) {
        return new SlurmJobCompleteProcessor(
                processEngine,
                externalTaskRetryPlanner,
                slurmExternalTaskFailureResolvers != null ? slurmExternalTaskFailureResolvers : List.of(),
                slurmProperties,
                slurmJobRepository);
    }

    @Bean
    public static SlurmFlagFileHandler slurmFlagFileHandler(
            ObjectProvider<ClientProperties> clientProperties,
            ObjectProvider<SlurmJobRepository> slurmJobRepository,
            SlurmJobCompleteProcessor slurmJobCompleteProcessor) {
        return new SlurmFlagFileHandler(clientProperties, slurmJobRepository, slurmJobCompleteProcessor);
    }

    /**
     * sacct 跟踪依赖 Mongo；无 {@link MongoTemplate} 时不注册本 Bean，{@link SlurmTaskManager} 通过 {@link ObjectProvider} 跳过落库。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(MongoTemplate.class)
    @EnableMongoRepositories(basePackageClasses = SlurmJobRepository.class)
    static class SlurmSacctMongoConfiguration {

        @Bean
        public SlurmJobTracker slurmJobTracker(
                SlurmProperties slurmProperties,
                SlurmFlagFileHandler slurmFlagFileHandler,
                SlurmJobRepository slurmJobRepository) {
            return new SlurmJobTracker(slurmProperties, slurmFlagFileHandler, slurmJobRepository);
        }
    }

    @Bean
    public static SlurmTaskManager slurmTaskManager(
            SlurmProperties slurmProperties,
            SlurmService slurmService,
            SlurmFlagFileHandler slurmFlagFileHandler,
            ObjectProvider<SlurmJobTracker> slurmJobTracker) {
        return new SlurmTaskManager(slurmProperties, slurmService, slurmFlagFileHandler, slurmJobTracker);
    }
}
