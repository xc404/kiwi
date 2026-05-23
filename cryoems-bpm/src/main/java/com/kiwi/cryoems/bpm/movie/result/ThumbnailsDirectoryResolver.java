package com.kiwi.cryoems.bpm.movie.result;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class ThumbnailsDirectoryResolver {

    public Path resolve( String workDir) {
        return Path.of(workDir.trim()).resolve("thumbnails").toAbsolutePath().normalize();
    }
}
