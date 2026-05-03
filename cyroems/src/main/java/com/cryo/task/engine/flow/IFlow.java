package com.cryo.task.engine.flow;

import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.TaskStep;

public interface IFlow<T extends Instance, R extends InstanceResult> {
//    public String getName();

    public TaskStep next(Context<T, R> context, TaskStep step);

}
