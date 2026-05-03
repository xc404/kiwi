package com.cryo.task.movie.handler.ctf;

import com.cryo.task.engine.HandlerKey;
import com.cryo.model.MovieResult;
import com.cryo.task.engine.StepResult;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.engine.Handler;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class Ctffind5Handler implements Handler<MovieContext>
{
    private final Ctffind5Support ctffind5Support;

    public Ctffind5Handler(Ctffind5Support ctffind5Support) {
        this.ctffind5Support = ctffind5Support;
    }

    @Override
    public HandlerKey support() {
        return HandlerKey.CTF_ESTIMATION;
    }

    public StepResult handle(MovieContext movieContext) {
        MovieResult result = movieContext.getResult();
        if( Optional.ofNullable(result.getCtfEstimation()).map(EstimationResult::getStigma_x).isEmpty() || movieContext.forceReset() ) {

            this.ctffind5Support.estimate(movieContext);
            return StepResult.success("CTF Estimation Success");
        }
        return StepResult.success("CTF Estimation skipped");
    }
}
