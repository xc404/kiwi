package com.cryo.task.engine.flow;

import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.TaskStep;

import java.util.Map;

public abstract class AbstractFlow<T extends Instance, R extends InstanceResult> implements IFlow<T,R>{

    private final Map<TaskStep, TaskStep> stepMap;

    protected AbstractFlow(Map<TaskStep, TaskStep> stepMap) {
        this.stepMap = stepMap;
    }


    @Override
    public  TaskStep next(Context<T,R> context, TaskStep currentStep) {
        return stepMap.get(currentStep);
    }
}
