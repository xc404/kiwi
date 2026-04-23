package com.cryo.task.movie.handler.slurm;

import com.cryo.task.engine.HandlerKey;
import org.springframework.stereotype.Service;

@Service
public class SlurmStepHandler extends AbstractSlurmHandler
{
    protected SlurmStepHandler(SlurmSupport slurmSupport) {
        super(slurmSupport);
    }

    @Override
    public HandlerKey support() {
        return HandlerKey.Slurm;
    }
}
