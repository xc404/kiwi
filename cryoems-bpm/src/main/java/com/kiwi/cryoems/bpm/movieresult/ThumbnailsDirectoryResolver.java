package com.kiwi.cryoems.bpm.movieresult;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Component
public class ThumbnailsDirectoryResolver {

    public Path resolve( String workDir) {
        return Path.of(workDir.trim()).resolve("thumbnails").toAbsolutePath().normalize();
    }
}
