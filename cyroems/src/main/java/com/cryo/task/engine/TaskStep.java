package com.cryo.task.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class TaskStep
{
    private Step step;
    private HandlerKey key;

    public static TaskStep of(Step name, HandlerKey handlerKey)
    {
        return new TaskStep(name, handlerKey);
    }

    public static TaskStep of(HandlerKey handlerKey)
    {
        return new TaskStep(Step.Default, handlerKey);
    }


//    public static TaskStep of(HandlerKey handlerKey) {
//        return new TaskStep(Step.Self, handlerKey);
//    }

    @Override
    public String toString() {
        return step + "_" + key;
    }

    public enum Step {
        Default,
        Self
    }
}
