package com.cryo.task.movie.handler;

import com.cryo.common.error.RetryException;
import com.cryo.common.utils.FileUtils;
import com.cryo.model.Movie;
import com.cryo.task.engine.HandlerKey;
import com.cryo.model.MovieImage;
import com.cryo.model.MovieResult;
import com.cryo.task.engine.StepResult;
import com.cryo.service.MovieService;
import com.cryo.task.engine.Handler;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.movie.handler.ctf.Ctffind5Support;
import com.cryo.task.movie.handler.ctf.EstimationResult;
import com.cryo.task.movie.handler.motion.MotionCor2Support;
import com.cryo.task.movie.handler.motion.MotionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;

import static com.cryo.task.utils.TaskUtils.checkFileExist;

@Service
@RequiredArgsConstructor
public class MovieResultHandler implements Handler<MovieContext>
{
    private final Ctffind5Support ctffind5Support;
    private final MotionCor2Support motionCor2Support;
    private final MovieService movieService;

    @Override
    public HandlerKey support() {
        return HandlerKey.Result;
    }

    @Override
    public StepResult handle(MovieContext movieContext) {
        MovieResult result = movieContext.getResult();
//        movie.setFile_create_at(FileUtils.lastModified(new File(movie.getFile_path())));
        MotionResult motionCorrection = result.getMotion();
        EstimationResult ctfEstimation = result.getCtfEstimation();
        String path = motionCorrection.getNo_dw().getPath();
        checkFileExist(path);
        checkFileExist(motionCorrection.getDw().getPath());
        checkFileExist(ctfEstimation.getOutputFile());
        checkFileExist(result.getImages().get(MovieImage.Type.ctf).getPath());
        checkFileExist(result.getImages().get(MovieImage.Type.motion_mrc).getPath());
        checkFileExist(ctfEstimation.getLogFile());
        try {
            movieService.createImage(movieContext, MovieImage.Type.patch_log);
            ctffind5Support.result(movieContext);
            motionCor2Support.handle(movieContext);
        } catch( Exception e ) {
            throw new RetryException(e);
        }
        return StepResult.success("Movie Result Success");

    }

}
