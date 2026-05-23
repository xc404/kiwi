package com.kiwi.cryoems.bpm.movie.result.motion;

import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class MotionResultValidator {

    public void validate(MovieResult result) {
        if (result.getMotion() == null) {
            return;
        }
        requireFile(result.getMotion().getNo_dw().getPath(), "motion no_dw mrc");
        requireFile(result.getMotion().getDw().getPath(), "motion dw mrc");
        if (result.getImages() != null) {
            MovieImage image = result.getImages().get(MovieImage.Type.motion_mrc);
            if (image != null) {
                requireFile(image.getPath(), "motion_mrc image");
            }
            MovieImage patchLog = result.getImages().get(MovieImage.Type.patch_log);
            if (patchLog != null) {
                requireFile(patchLog.getPath(), "patch_log image");
            }
        }
    }

    private static void requireFile(String path, String label) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        if (!Files.isRegularFile(Path.of(path.trim()))) {
            throw new IllegalStateException(label + " 不存在: " + path);
        }
    }
}
