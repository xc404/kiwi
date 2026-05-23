package com.kiwi.cryoems.bpm.movie.result.motion;

import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * MotionCor2 业务：路径推断、缩略图生成、写入 {@link MovieResult}、校验。
 */
@Service
@RequiredArgsConstructor
public class MotionResultSection {

    private final MotionPathResolver pathResolver;
    private final MotionThumbnailGenerator thumbnailGenerator;
    private final MotionPatchLogThumbnailGenerator patchLogThumbnailGenerator;
    private final MotionResultApplier applier;
    private final MotionResultValidator validator;

    public void process(
            MovieResult result,
            String motionNoDwMrc,
            String motionVersion,
            Path thumbnailsDir,
            Double predictDose)
            throws Exception {
        MotionPaths paths = pathResolver.resolve(motionNoDwMrc, motionVersion, thumbnailsDir);
        thumbnailGenerator.generate(paths);
        patchLogThumbnailGenerator.generate(paths);
        applier.apply(result, paths, predictDose);
        validator.validate(result);
    }
}
