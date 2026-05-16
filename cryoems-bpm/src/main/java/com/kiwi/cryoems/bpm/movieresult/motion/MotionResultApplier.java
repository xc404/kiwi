package com.kiwi.cryoems.bpm.movieresult.motion;

import com.kiwi.cryoems.bpm.model.MovieImage;
import com.kiwi.cryoems.bpm.model.MovieResult;
import com.kiwi.cryoems.bpm.model.motion.MotionFile;
import com.kiwi.cryoems.bpm.model.motion.MotionResult;
import com.kiwi.cryoems.bpm.model.motion.MrcFile;
import org.springframework.stereotype.Component;

@Component
public class MotionResultApplier {

    public void apply(MovieResult result, MotionPaths paths) {
        MotionResult motion = new MotionResult();
        motion.setNo_dw(new MrcFile(paths.noDwMrc()));
        motion.setDw(new MrcFile(paths.dwMrc()));
        motion.setDws(new MrcFile(paths.dwsMrc()));
        motion.setLocal_motion(new MotionFile(paths.localLog()));
        motion.setRigid_motion(new MotionFile(paths.rigidLog()));
        motion.setSubtarctionOutput(paths.subtarctionMrc());
        result.setMotion(motion);
        result.addImage(new MovieImage(MovieImage.Type.motion_mrc, paths.mrcImage()));
    }
}
