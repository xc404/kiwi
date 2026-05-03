package com.cryo.task.engine;

public abstract class EmptyHandler<T extends Context> implements Handler<T >
{
    @Override
    public StepResult handle(T context) {
        return StepResult.success("success");
    }
}
