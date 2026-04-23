package com.cryo.task.movie.handler.slurm;

import com.cryo.task.engine.Context;
import com.cryo.task.engine.StepResult;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.engine.Handler;

public abstract class AbstractSlurmHandler<T extends Context> implements Handler<T>
{
    private final SlurmSupport slurmSupport;

    protected AbstractSlurmHandler(SlurmSupport slurmSupport) {
        this.slurmSupport = slurmSupport;
    }


    @Override
    public StepResult handle(T movieContext) {
        return slurmSupport.handle(movieContext, movieContext.getCurrentStep());
    }
}
