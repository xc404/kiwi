package com.cryo.task.engine;

import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.settings.TaskSettings;
import com.cryo.task.engine.flow.IFlow;


public interface Context<T extends Instance, R extends InstanceResult>
{
    T getInstance();

    void start();

    Task getTask();

    void complete();

    void setCurrentStep(TaskStep step);

    R getResult();

    IFlow<T,R> getFlow();

    TaskDataset getTaskDataset();

    String getContextDir();

    default boolean autoNext(){
        return true;
    }

    TaskStep getCurrentStep();

    TaskSettings getTaskSettings();
}
