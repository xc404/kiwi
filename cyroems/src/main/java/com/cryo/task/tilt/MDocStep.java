package com.cryo.task.tilt;

import com.cryo.task.engine.HandlerKey;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class MDocStep
{
    private Step step;
    private HandlerKey key;

    public static MDocStep of(Step name, HandlerKey handlerKey)
    {
        return new MDocStep(name, handlerKey);
    }

    public static MDocStep of(HandlerKey handlerKey)
    {
        return new MDocStep(Step.Default, handlerKey);
    }


    public static MDocStep self(HandlerKey handlerKey) {
        return new MDocStep(Step.Self, handlerKey);
    }

    @Override
    public String toString() {
        return step + "_" + key;
    }

    public enum Step {
        Default,
        Self
    }
}
