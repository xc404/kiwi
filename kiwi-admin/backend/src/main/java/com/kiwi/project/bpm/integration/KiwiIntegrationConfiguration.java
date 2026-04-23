package com.kiwi.project.bpm.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KiwiIntegrationProperties.class)
public class KiwiIntegrationConfiguration {
}
