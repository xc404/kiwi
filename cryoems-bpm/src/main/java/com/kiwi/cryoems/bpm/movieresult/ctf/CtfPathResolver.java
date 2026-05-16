package com.kiwi.cryoems.bpm.movieresult.ctf;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Component
public class CtfPathResolver {

    static final String OUTPUT_SUFFIX = "_freq.mrc";
    static final String LOG_SUFFIX = "_freq.txt";
    static final String AVROT_SUFFIX = "_freq_avrot.txt";

    public CtfPaths resolve(String ctfOutputFile, String fileName, Path thumbnailsDir) {
        requireText(ctfOutputFile, "ctfOutputFile");
        Path ctfMrc = Path.of(ctfOutputFile.trim()).toAbsolutePath().normalize();
        String ctfPath = ctfMrc.toString();
        String logFile;
        String avrotFile;
        if (ctfPath.endsWith(OUTPUT_SUFFIX)) {
            String prefix = ctfPath.substring(0, ctfPath.length() - OUTPUT_SUFFIX.length());
            logFile = prefix + LOG_SUFFIX;
            avrotFile = prefix + AVROT_SUFFIX;
        } else {
            logFile = replaceExtension(ctfPath, ".txt");
            avrotFile = ctfPath + "_avrot.txt";
        }
        String image = thumbnailsDir.resolve(fileName + "_freq.png").toString();
        return new CtfPaths(ctfPath, logFile, avrotFile, image);
    }

    public String resolveFileName(String ctfOutputFile) {
        Path ctfMrc = Path.of(ctfOutputFile.trim()).toAbsolutePath().normalize();
        String name = ctfMrc.getFileName().toString();
        if (name.endsWith(OUTPUT_SUFFIX)) {
            return name.substring(0, name.length() - OUTPUT_SUFFIX.length());
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
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
