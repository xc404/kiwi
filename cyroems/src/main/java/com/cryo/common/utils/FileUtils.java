package com.cryo.common.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Date;

public class FileUtils {


    public static Date lastModified(File file) {
        long lastModified = file.lastModified();
        return new Date(lastModified);
    }

    public static Instant created(File file) {
        BasicFileAttributes attr = null;
        try {
            Path path = file.toPath();
            attr = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 创建时间
        return attr.creationTime().toInstant();
    }

    public static BasicFileAttributes attr(File file) {
        try {
            Path path = file.toPath();
            BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
            return attr;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // 创建时间
    }
}
