package com.cryo.task.movie.handler.vfm;

import com.cryo.model.MovieResult;
import com.cryo.model.export.ExportMovie;
import com.cryo.task.engine.BaseContext;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.movie.MovieContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
public class VFMHandler implements Handler<BaseContext>
{
    private final VFMSupport support;

    @Override
    public HandlerKey support() {
        return HandlerKey.VFM;
    }

    @Override
    public StepResult handle(BaseContext movieContext) {
        MovieResult result = (MovieResult) movieContext.getResult();
        if( result.getVfmResult() == null || movieContext.forceReset() || ! new File(result.getVfmResult().getPngFile()).exists() || movieContext.getInstance() instanceof ExportMovie) {

            return support.handle(movieContext);
        }
        return StepResult.success("vfm skipped");

    }
}
