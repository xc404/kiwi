package com.kiwi.cryoems.bpm.movieresult.ctf;

/**
 * CTFFIND5 步骤产物路径（对齐 cyroems {@code Ctffind5Support#estimate}）。
 */
public record CtfPaths(String outputMrc, String logFile, String avrotFile, String image) {}
