package com.kiwi.bpmn.core.retry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SelectiveFailedJobRetryConfiguration {

    @Bean
    @ConditionalOnMissingBean(JobRetryExceptionClassifier.class)
    JobRetryExceptionClassifier defaultJobRetryExceptionClassifier() {
        return failure -> true;
    }
}
