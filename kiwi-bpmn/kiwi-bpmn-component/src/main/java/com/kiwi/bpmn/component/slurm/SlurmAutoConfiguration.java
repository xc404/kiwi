package com.kiwi.bpmn.component.slurm;

import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "kiwi.bpm.slurm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SlurmProperties.class})
@EnableScheduling
@Slf4j
public class SlurmAutoConfiguration {

    @Bean
    public static SlurmEnabledConditionsValidator slurmEnabledConditionsValidator(
            SlurmProperties slurmProperties,
            ObjectProvider<SlurmJobTracker> slurmJobTracker,
            ObjectProvider<SlurmJobRepository> slurmJobRepository) {
        return new SlurmEnabledConditionsValidator(slurmProperties, slurmJobTracker, slurmJobRepository);
    }

    @Bean
    public static SlurmService slurmService(SlurmProperties slurmProperties) {
        return new SlurmService(slurmProperties);
    }

    @Bean
    public static DefaultSlurmExternalTaskFailureResolver defaultSlurmExternalTaskFailureResolver() {
        return new DefaultSlurmExternalTaskFailureResolver();
    }

    /**
     * sacct 终态上报与 Mongo 跟踪。
     * <p>
     * 改造说明：原使用 {@code @ConditionalOnBean(SlurmJobRepository.class)} 控制注册，但
     * {@code @ConditionalOnBean} 的求值依赖 BeanDefinition 注册顺序——本类是组件扫描到的普通 {@code @Configuration}，
     * 远端打包后的 class 扫描顺序与本地不同，可能在 {@code MongoModule} 的 {@code @EnableMongoRepositories}
     * 完成注册之前求值，导致条件为 false、整个内部配置被跳过（{@link SlurmJobTracker} 缺失，
     * 进而触发 {@link SlurmEnabledConditionsValidator} 启动失败）。
     * <p>
     * 改为 {@link ConditionalOnClass}({@code MongoTemplate.class})：仅依赖类路径，与扫描顺序无关；
     * 当类路径上确无 spring-data-mongodb 时跳过整个 Mongo 跟踪。运行时 Mongo 仍未配置则
     * 由 {@link SlurmEnabledConditionsValidator} 给出友好错误（验证器在 {@code SlurmAutoConfiguration}
     * 中第一个声明，{@code preInstantiateSingletons} 时优先实例化）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MongoTemplate.class)
    static class SlurmJobMongoTrackingConfiguration {

        @Bean
        public static SlurmJobCompleteProcessor slurmJobCompleteProcessor(
                ProcessEngine processEngine,
                ObjectProvider<ExternalTaskRetryPlanner> externalTaskRetryPlanner,
                List<SlurmExternalTaskFailureResolver> slurmExternalTaskFailureResolvers,
                DefaultSlurmExternalTaskFailureResolver defaultSlurmExternalTaskFailureResolver,
                SlurmProperties slurmProperties,
                SlurmService slurmService,
                SlurmJobRepository slurmJobRepository) {
            return new SlurmJobCompleteProcessor(
                    processEngine,
                    externalTaskRetryPlanner,
                    slurmExternalTaskFailureResolvers != null ? slurmExternalTaskFailureResolvers : List.of(),
                    defaultSlurmExternalTaskFailureResolver,
                    slurmProperties,
                    slurmService,
                    slurmJobRepository);
        }

        @Bean
        public SlurmJobTracker slurmJobTracker(
                SlurmProperties slurmProperties,
                SlurmJobCompleteProcessor slurmJobCompleteProcessor,
                SlurmJobRepository slurmJobRepository) {
            return new SlurmJobTracker(slurmProperties, slurmJobCompleteProcessor, slurmJobRepository);
        }

        @Bean
        public static SlurmTaskManager slurmTaskManager(
                SlurmProperties slurmProperties,
                SlurmService slurmService,
                SlurmJobTracker slurmJobTracker) {
            return new SlurmTaskManager(slurmProperties, slurmService, slurmJobTracker);
        }
    }
}
