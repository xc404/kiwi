package com.cryo.integration.workflow;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(KiwiWorkflowProperties.class)
public class WorkflowIntegrationConfiguration {
}
