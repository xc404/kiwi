package com.kiwi.bpmn.core.variable;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.spring.boot.starter.configuration.Ordering;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(Ordering.DEFAULT_ORDER + 1)
public class VariableProcessEnginePlugin implements ProcessEnginePlugin
{
    private final InputMappingBpmnParseListener inputMappingBpmnParseListener;

    public VariableProcessEnginePlugin(InputMappingBpmnParseListener inputMappingBpmnParseListener) {
        this.inputMappingBpmnParseListener = inputMappingBpmnParseListener;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        processEngineConfiguration.getCustomPostBPMNParseListeners().add(inputMappingBpmnParseListener);
    }

    @Override
    public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
    }

    @Override
    public void postProcessEngineBuild(ProcessEngine processEngine) {

    }
}
