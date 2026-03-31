package com.kiwi.bpmn.external;

import org.camunda.bpm.client.impl.ExternalTaskClientBuilderImpl;
import org.camunda.bpm.engine.ProcessEngine;

public class LocalExternalTaskClientBuild extends ExternalTaskClientBuilderImpl
{
    private final ProcessEngine processEngine;

    public LocalExternalTaskClientBuild(ProcessEngine processEngine) {
        this.processEngine = processEngine;
    }

    protected void initEngineClient() {
        engineClient = new LocalEngineClient(processEngine, workerId, maxTasks, usePriority);
    }
}
