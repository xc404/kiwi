package com.kiwi.cryoems.bpm.movieresult.vfm;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 对齐 cyroems {@code VFMSupport} 将 VFM PNG 复制到 thumbnails 目录。
 */
@Component
public class VfmPngCopier {

    public void copyToThumbnails(VfmPaths paths) throws IOException {
        Path source = Path.of(paths.pngFile());
        Path target = Path.of(paths.image());
        if (!Files.isRegularFile(source)) {
            return;
        }
        if (source.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize())) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }
}
