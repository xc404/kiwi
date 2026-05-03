package com.cryo.task.engine;

import lombok.Data;

import java.util.Date;

@Data
public class StepOutput
{


    private TaskStep step;
    private StepResult result;
    private Date startTime;
    private Date endTime;

    public static StepOutput step(Date startTime, TaskStep step, StepResult stepResult) {
        StepOutput stepOutput = new StepOutput();
        stepOutput.setEndTime(new Date());
        stepOutput.setStartTime(startTime);
        stepOutput.setResult(stepResult);
        stepOutput.setStep(step);
        return stepOutput;
    }
}
