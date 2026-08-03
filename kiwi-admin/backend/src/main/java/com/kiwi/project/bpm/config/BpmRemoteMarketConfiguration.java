package com.kiwi.project.bpm.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BpmRemoteMarketProperties.class)
public class BpmRemoteMarketConfiguration {
}
