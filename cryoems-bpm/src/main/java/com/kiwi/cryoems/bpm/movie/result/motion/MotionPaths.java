package com.kiwi.cryoems.bpm.movie.result.motion;

/**
 * MotionCor2 步骤产物路径（对齐 cyroems {@code MotionCor2#getMotionCorrectionOutput}）。
 */
public record MotionPaths(
        String fileName,
        String noDwMrc,
        String dwMrc,
        String dwsMrc,
        String localLog,
        String rigidLog,
        String subtarctionMrc,
        String mrcImage,
        String patchLogImage) {}
