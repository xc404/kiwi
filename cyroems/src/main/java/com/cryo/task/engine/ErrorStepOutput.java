package com.cryo.task.engine;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ErrorStepOutput  {
    private HandlerKey step;
    private String error;
}
