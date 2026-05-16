package com.kiwi.cryoems.bpm.movieresult.ctf;

import com.kiwi.cryoems.bpm.model.MovieImage;
import com.kiwi.cryoems.bpm.model.MovieResult;
import com.kiwi.cryoems.bpm.model.ctf.EstimationResult;
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
