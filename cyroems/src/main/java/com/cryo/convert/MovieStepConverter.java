package com.cryo.convert;

import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Service;

public class MovieStepConverter implements Converter<String, TaskStep>
{
    @Override
    public TaskStep convert(String source) {
        return TaskStep.of(HandlerKey.valueOf(source));
    }

}
