package com.kiwi.cryoems.bpm.movie.result.ctf;

import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class CtfResultValidator {

    public void validate(MovieResult result) {
        if (result.getCtfEstimation() == null) {
            return;
        }
        requireFile(result.getCtfEstimation().getOutputFile(), "ctf output mrc");
        requireFile(result.getCtfEstimation().getLogFile(), "ctf log");
        if (result.getImages() != null) {
            MovieImage image = result.getImages().get(MovieImage.Type.ctf);
            if (image != null) {
                requireFile(image.getPath(), "ctf image");
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
