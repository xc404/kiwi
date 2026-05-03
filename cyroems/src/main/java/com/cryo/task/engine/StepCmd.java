package com.cryo.task.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StepCmd
{
    private String key;
    private TaskStep exeStep;
    private String cmd;
}
