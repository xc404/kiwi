package com.cryo.task.engine;

public interface Handler<T extends Context>
{
    public HandlerKey support();
    public StepResult handle(T context);
}
