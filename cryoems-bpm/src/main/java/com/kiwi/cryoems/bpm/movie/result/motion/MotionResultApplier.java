package com.kiwi.cryoems.bpm.movie.result.motion;

import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import com.kiwi.cryoems.bpm.movie.model.motion.MotionFile;
import com.kiwi.cryoems.bpm.movie.model.motion.MotionResult;
import com.kiwi.cryoems.bpm.movie.model.motion.MrcFile;
import org.springframework.stereotype.Component;

@Component
public class MotionResultApplier {

    public void apply(MovieResult result, MotionPaths paths, Double predictDose) {
        MotionResult motion = new MotionResult();
        motion.setNo_dw(new MrcFile(paths.noDwMrc()));
        motion.setDw(new MrcFile(paths.dwMrc()));
        motion.setDws(new MrcFile(paths.dwsMrc()));
        motion.setLocal_motion(new MotionFile(paths.localLog()));
        motion.setRigid_motion(new MotionFile(paths.rigidLog()));
        motion.setSubtarctionOutput(paths.subtarctionMrc());
        motion.setPredict_dose(predictDose);
        result.setMotion(motion);
        result.addImage(new MovieImage(MovieImage.Type.motion_mrc, paths.mrcImage()));
        result.addImage(new MovieImage(MovieImage.Type.patch_log, paths.patchLogImage()));
    }
}
