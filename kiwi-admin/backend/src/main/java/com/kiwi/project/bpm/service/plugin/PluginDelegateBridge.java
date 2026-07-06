package com.kiwi.project.bpm.service.plugin;

import org.operaton.bpm.client.task.ExternalTask;
import org.operaton.bpm.client.task.ExternalTaskHandler;
import org.operaton.bpm.client.task.ExternalTaskService;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 将子上下文内的 delegate 桥接至宿主，供 Operaton {@code getBean(beanName)} 解析。
 * {@code registerSingleton} 不触发 {@code FactoryBean} 语义，故直接实现目标接口并懒委托。
 */
public final class PluginDelegateBridge {

    private PluginDelegateBridge() {}

    public static JavaDelegate javaDelegate(ConfigurableApplicationContext pluginContext, String beanName) {
        return new JavaDelegate() {
            private volatile JavaDelegate delegate;

            @Override
            public void execute(DelegateExecution execution) throws Exception {
                if (delegate == null) {
                    delegate = pluginContext.getBean(beanName, JavaDelegate.class);
                }
                delegate.execute(execution);
            }
        };
    }

    public static ExternalTaskHandler externalTaskHandler(
            ConfigurableApplicationContext pluginContext, String beanName) {
        return new ExternalTaskHandler() {
            private volatile ExternalTaskHandler delegate;

            @Override
            public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
                if (delegate == null) {
                    delegate = pluginContext.getBean(beanName, ExternalTaskHandler.class);
                }
                delegate.execute(externalTask, externalTaskService);
            }
        };
    }
}
