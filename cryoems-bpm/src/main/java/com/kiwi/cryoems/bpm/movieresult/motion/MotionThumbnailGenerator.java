package com.kiwi.cryoems.bpm.movieresult.motion;

import com.kiwi.cryoems.bpm.movieresult.ShellCommandRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 对齐 cyroems {@code MovieService#createMotionMrc} / {@code SoftwareService#mrc_png}。
 */
@Component
@RequiredArgsConstructor
public class MotionThumbnailGenerator {

    private final ShellCommandRunner shellCommandRunner;

    @Value("${cryoems.bpm.mrc-png-command:mrc_png.sh}")
    private String mrcPngCommand;

    public void generate(MotionPaths paths) throws IOException, InterruptedException {
        Path output = Path.of(paths.mrcImage());
        Files.createDirectories(output.getParent());
        shellCommandRunner.run(mrcPngCommand, paths.dwMrc(), paths.mrcImage());
    }
}
