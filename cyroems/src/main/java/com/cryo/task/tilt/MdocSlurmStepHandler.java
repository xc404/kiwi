package com.cryo.task.tilt;

import com.cryo.model.InstanceResult;
import com.cryo.model.MDocResult;
import com.cryo.model.MovieImage;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.movie.handler.slurm.AbstractSlurmHandler;
import com.cryo.task.movie.handler.slurm.SlurmSupport;
import com.cryo.task.utils.TaskUtils;
import org.springframework.stereotype.Service;

@Service
public class MdocSlurmStepHandler extends AbstractSlurmHandler<MDocContext>
{
    protected MdocSlurmStepHandler(SlurmSupport slurmSupport) {
        super(slurmSupport);
    }

    @Override
    public HandlerKey support() {
        return HandlerKey.MDOC_SLURM;
    }

    @Override
    public StepResult handle(MDocContext movieContext) {
        StepResult handle = super.handle(movieContext);
        handle.setPersistent(true);
        MDocResult result = movieContext.getResult();
        try {
            TaskUtils.checkFileExist(result.getAlignReconResult().getAlign_reconOutput());
        }catch( Exception e ){
            handle.setSuccess(false);
            handle.setMessage(e.getMessage());
            return handle;
        }
        movieContext.getResult().addImage(new MovieImage(MovieImage.Type.mdoc_recon, result.getAlignReconResult().getAlign_reconOutput()));
        return handle;
    }
}
