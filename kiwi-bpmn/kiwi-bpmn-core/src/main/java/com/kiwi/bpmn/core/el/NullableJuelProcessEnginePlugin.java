package com.kiwi.bpmn.core.el;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.el.JuelExpressionManager;
import org.camunda.bpm.spring.boot.starter.configuration.Ordering;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * 在引擎完成 {@code initExpressionManager()} 之后，向现有
 * {@link JuelExpressionManager}（通常为 {@code SpringExpressionManager}）的 EL 链末尾追加
 * {@link MissingIdentifierNullElResolver}。
 * <p>
 * 不得在 {@code preInit} 中替换 {@code ExpressionManager}，否则会丢失 Spring
 * {@code ApplicationContext} 下的 Bean 解析（如 {@code ${assignmentActivity}}）。
 */
@Service
@Order(Ordering.DEFAULT_ORDER)
@ConditionalOnProperty(
        prefix = "kiwi.bpm",
        name = "juel-null-for-missing-variables",
        havingValue = "true",
        matchIfMissing = true)
public class NullableJuelProcessEnginePlugin implements ProcessEnginePlugin {

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {}

    @Override
    public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        if (processEngineConfiguration.getExpressionManager() instanceof JuelExpressionManager juel) {
            JuelElResolverAugmentation.appendMissingIdentifierNullResolver(juel);
        }
    }

    @Override
    public void postProcessEngineBuild(ProcessEngine processEngine) {}
}
