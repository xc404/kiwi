package com.kiwi.cryoems.bpm.movieresult;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Component
public class ThumbnailsDirectoryResolver {

    public Path resolve(String motionNoDwMrc, String workDir) {
        if (StringUtils.hasText(workDir)) {
            return Path.of(workDir.trim()).resolve("thumbnails").toAbsolutePath().normalize();
        }
        Path motionMrc = Path.of(motionNoDwMrc.trim()).toAbsolutePath().normalize();
        Path motionDir = motionMrc.getParent();
        if (motionDir == null) {
            throw new IllegalArgumentException("无法从 motionNoDwMrc 推断 thumbnails 目录: " + motionNoDwMrc);
        }
        Path parent = motionDir.getParent();
        if (parent == null) {
            return motionDir.resolve("thumbnails");
        }
        return parent.resolve("thumbnails");
    }
}
