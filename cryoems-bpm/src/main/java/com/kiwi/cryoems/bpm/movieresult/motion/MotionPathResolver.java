package com.kiwi.cryoems.bpm.movieresult.motion;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Component
public class MotionPathResolver {

    public MotionPaths resolve(String motionNoDwMrc, String motionVersion, Path thumbnailsDir) {
        requireText(motionNoDwMrc, "motionNoDwMrc");
        Path motionMrc = Path.of(motionNoDwMrc.trim()).toAbsolutePath().normalize();
        Path motionDir = motionMrc.getParent();
        String fileName = stripExtension(motionMrc.getFileName().toString());

        String dw = motionDir.resolve(fileName + "_DW.mrc").toString();
        String dws = motionDir.resolve(fileName + "_DWS.mrc").toString();
        String subtarction = motionDir.resolve(fileName + "_subtarction.mrc").toString();
        String logBase = motionDir.resolve(fileName + "_log").toString();
        String localLog;
        String rigidLog;
        if (StringUtils.hasText(motionVersion) && motionVersion.trim().startsWith("1.6")) {
            localLog = motionDir.resolve(fileName + "-Patch-Patch.log").toString();
            rigidLog = motionDir.resolve(fileName + "-Patch-Full.log").toString();
        } else {
            localLog = logBase + "0-Patch-Patch.log";
            rigidLog = logBase + "0-Patch-Full.log";
        }
        String mrcImage = thumbnailsDir.resolve(fileName + "_DW_thumb_@1024.png").toString();

        return new MotionPaths(fileName, motionMrc.toString(), dw, dws, localLog, rigidLog, subtarction, mrcImage);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("流程变量 " + name + " 不能为空");
        }
    }
}
