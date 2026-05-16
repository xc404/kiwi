package com.kiwi.cryoems.bpm.movieresult.vfm;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Component
public class VfmPathResolver {

    static final String LOG_SUFFIX = "_predicted_boxes.txt";

    public VfmPaths resolve(String vfmLogFile, Path thumbnailsDir) {
        requireText(vfmLogFile, "vfmLogFile");
        Path vfmLog = Path.of(vfmLogFile.trim()).toAbsolutePath().normalize();
        String logPath = vfmLog.toString();
        String pngFile;
        if (logPath.endsWith(LOG_SUFFIX)) {
            pngFile = logPath.substring(0, logPath.length() - LOG_SUFFIX.length()) + ".png";
        } else {
            pngFile = replaceExtension(logPath, ".png");
        }
        String image = thumbnailsDir.resolve("vfm_" + Path.of(pngFile).getFileName()).toString();
        return new VfmPaths(logPath, pngFile, image);
    }

    private static String replaceExtension(String path, String newExt) {
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return path + newExt;
        }
        return path.substring(0, dot) + newExt;
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("流程变量 " + name + " 不能为空");
        }
    }
}
