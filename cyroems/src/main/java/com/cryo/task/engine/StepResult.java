package com.cryo.task.engine;

import com.cryo.common.error.FatalException;
import com.cryo.common.error.RetryException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public final class StepResult
{
    private boolean success;
    private boolean persistent;
    private String message;
    private boolean retryable;
    private boolean fatal;
    private boolean waitCondition = false;
    private Map<String,Object> data = new HashMap<>();

    public static StepResult success(String message)
    {
        return new StepResult(true, true, message, false, false, false, new HashMap<>());
    }

    public static StepResult waitCondition(String message){
        return new StepResult(true, true, message, false, false, true, new HashMap<>());
    }

    public static StepResult retryError(String message)
    {
        return new StepResult(false, true, message, true, false, false,new HashMap<>());
    }
    public static StepResult fatalError(String message)
    {
        return new StepResult(false, true, message, false, true,false, new HashMap<>());
    }
    public static StepResult error(String message)
    {
        return new StepResult(false, true, message, false, false,false, new HashMap<>());
    }

    public static StepResult error(Exception e)
    {
        if(e instanceof RetryException ){
            return StepResult.retryError(e.getMessage());
        }
        if(e instanceof FatalException ){
            return StepResult.fatalError(e.getMessage());
        }
        return StepResult.error(e.getMessage());
    }
}
