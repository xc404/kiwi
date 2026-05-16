package com.kiwi.cryoems.bpm.movieresult.ctf;

import com.kiwi.cryoems.bpm.movieresult.ShellCommandRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 对齐 cyroems {@code MovieService#createCtf} / {@code SoftwareService#ctf_png}。
 */
@Component
@RequiredArgsConstructor
public class CtfThumbnailGenerator {

    private final ShellCommandRunner shellCommandRunner;

    @Value("${cryoems.bpm.ctf-png-command:ctf_png.sh}")
    private String ctfPngCommand;

    public void generate(CtfPaths paths) throws IOException, InterruptedException {
        Path output = Path.of(paths.image());
        Files.createDirectories(output.getParent());
        shellCommandRunner.run(ctfPngCommand, paths.outputMrc(), paths.image());
    }
}
