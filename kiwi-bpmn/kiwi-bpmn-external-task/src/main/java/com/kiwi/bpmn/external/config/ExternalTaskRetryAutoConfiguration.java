package com.kiwi.bpmn.external.config;

import com.kiwi.bpmn.core.jobretry.JobRetryExceptionClassifier;
import com.kiwi.bpmn.external.retry.ExternalTaskRetryCycleResolver;
import com.kiwi.bpmn.external.retry.ExternalTaskRetryPlanner;
import org.camunda.bpm.engine.RepositoryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(prefix = "kiwi.bpm.external-task-retry", name = "enabled", havingValue = "true")
@ConditionalOnBean(RepositoryService.class)
public class ExternalTaskRetryAutoConfiguration {

    @Bean
    public ExternalTaskRetryCycleResolver externalTaskRetryCycleResolver(RepositoryService repositoryService) {
        return new ExternalTaskRetryCycleResolver(repositoryService);
    }

    @Bean(name = "externalTaskRetryEngineDefaultCycle")
    public String externalTaskRetryEngineDefaultCycle(Environment env, ExternalTaskRetryProperties props) {
        String own = props.getDefaultTimeCycle();
        if (own != null && !own.isBlank()) {
            return own.trim();
        }
        return env.getProperty(
                "camunda.bpm.generic-properties.properties.failedJobRetryTimeCycle",
                env.getProperty("CAMUNDA_FAILED_JOB_RETRY_TIME_CYCLE", "R5/PT1M"));
    }

    @Bean
    public ExternalTaskRetryPlanner externalTaskRetryPlanner(
            JobRetryExceptionClassifier classifier,
            ExternalTaskRetryCycleResolver retryCycleResolver,
            @Qualifier("externalTaskRetryEngineDefaultCycle") String engineDefaultCycle) {
        return new ExternalTaskRetryPlanner(classifier, retryCycleResolver, engineDefaultCycle);
    }
}
