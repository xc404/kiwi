package com.cryo.task;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutorConfiguration {

    @Value("${app.task.default.maxPoolSize:100}")
    private int maxPoolSize;


    @Bean
    @Primary
    public ThreadPoolTaskExecutor taskExecutor() {

        ThreadPoolTaskExecutor poolTaskExecutor = new ThreadPoolTaskExecutor();
        poolTaskExecutor.setThreadNamePrefix("application-task-");
        poolTaskExecutor.setMaxPoolSize(maxPoolSize);
        poolTaskExecutor.setCorePoolSize(10);
        poolTaskExecutor.initialize();
        return poolTaskExecutor;
    }
}
