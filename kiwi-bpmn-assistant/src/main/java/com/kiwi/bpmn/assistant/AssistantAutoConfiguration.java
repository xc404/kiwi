package com.kiwi.bpmn.assistant;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@EnableConfigurationProperties(AssistantProperties.class)
@ComponentScan(basePackageClasses = AssistantAutoConfiguration.class)
public class AssistantAutoConfiguration {
}
