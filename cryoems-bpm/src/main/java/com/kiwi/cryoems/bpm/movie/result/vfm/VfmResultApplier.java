package com.kiwi.cryoems.bpm.movie.result.vfm;

import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import com.kiwi.cryoems.bpm.movie.model.vfm.VFMResult;
import org.springframework.stereotype.Component;

@Component
public class VfmResultApplier {

    public void apply(MovieResult result, VfmPaths paths) {
        VFMResult vfm = new VFMResult();
        vfm.setLogFile(paths.logFile());
        vfm.setPngFile(paths.pngFile());
        result.setVfmResult(vfm);
        result.addImage(new MovieImage(MovieImage.Type.vfm, paths.image()));
    }
}
