package com.kiwi.bpmn.core.async;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.spring.boot.starter.configuration.Ordering;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * 注册 {@link DefaultAsyncBeforeBpmnParseListener}。仅当 {@code kiwi.bpm.default-async-before-enabled=true} 时生效，
 * 便于通过配置关闭或删除本插件类实现回滚试验。
 */
@Service
@Order(Ordering.DEFAULT_ORDER + 10)
@ConditionalOnProperty(prefix = "kiwi.bpm", name = "default-async-before-enabled", havingValue = "true")
public class DefaultAsyncBeforeProcessEnginePlugin implements ProcessEnginePlugin {

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        processEngineConfiguration
                .getCustomPostBPMNParseListeners()
                .add(new DefaultAsyncBeforeBpmnParseListener());
    }

    @Override
    public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {}

    @Override
    public void postProcessEngineBuild(ProcessEngine processEngine) {}
}
