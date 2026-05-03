package com.kiwi.bpmn.core.retry;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultFailedJobCommandFactory;
import org.camunda.bpm.engine.impl.jobexecutor.FailedJobCommandFactory;
import org.camunda.bpm.spring.boot.starter.configuration.Ordering;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在 {@link org.camunda.bpm.engine.impl.jobexecutor.FailedJobListener} 调用的
 * {@link FailedJobCommandFactory} 上包装一层，实现「仅当 {@link JobRetryExceptionClassifier}
 * 允许时才使用引擎默认 Job 重试」。
 * <p>
 * 排序在 {@link com.kiwi.bpmn.core.async.DefaultAsyncBeforeProcessEnginePlugin}（+10）之后，
 * 以便在 Spring Boot 默认 {@code DefaultFailedJobConfiguration} 已设置工厂后再包装。
 * <p>
 * {@link org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseListener} 仅作用于解析期，无法按运行期异常类型分支；
 * 引擎也未对业务代码开放与 {@code FailedJobListener} 同级的可注册 Listener，因此用工厂扩展点实现。
 */
@Service
@Order(Ordering.DEFAULT_ORDER + 30)
public class SelectiveFailedJobRetryEnginePlugin implements ProcessEnginePlugin {

    private final List<JobRetryExceptionClassifier> classifier;

    public SelectiveFailedJobRetryEnginePlugin(List<JobRetryExceptionClassifier> classifier) {
        this.classifier = classifier;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        FailedJobCommandFactory existing = processEngineConfiguration.getFailedJobCommandFactory();
        if (existing == null) {
            existing = new DefaultFailedJobCommandFactory();
        }
        processEngineConfiguration.setFailedJobCommandFactory(
                new SelectiveFailedJobCommandFactory(existing, classifier));
    }

    @Override
    public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    }

    @Override
    public void postProcessEngineBuild(ProcessEngine processEngine) {
    }
}
