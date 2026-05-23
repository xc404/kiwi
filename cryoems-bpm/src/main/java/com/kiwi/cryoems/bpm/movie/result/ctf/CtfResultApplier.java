package com.kiwi.cryoems.bpm.movie.result.ctf;

import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import com.kiwi.cryoems.bpm.movie.model.ctf.EstimationResult;
import org.springframework.stereotype.Component;

@Component
public class CtfResultApplier {

    public void apply(MovieResult result, CtfPaths paths) {
        EstimationResult estimation = new EstimationResult();
        estimation.setOutputFile(paths.outputMrc());
        estimation.setLogFile(paths.logFile());
        estimation.setAvrotFile(paths.avrotFile());
        result.setCtfEstimation(estimation);
        result.addImage(new MovieImage(MovieImage.Type.ctf, paths.image()));
    }
}
