package com.kiwi.cryoems.bpm.movie.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 执行 Titan contrast_mean 并换算 predict dose，对齐 cyroems {@code MotionCor2Support#predictDose}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PredictDoseRunner {

    private static final double DOSE_SCALE = 5.1;

    private final PredictDoseCommandRegistry commandRegistry;

    @Value("${cryoems.bpm.predict-dose.timeout-seconds:600}")
    private long commandTimeoutSeconds;

    public double predict(String motionNoDwMrc, String microscope, double effectivePixelSize) throws IOException, InterruptedException {
        return predict(motionNoDwMrc, microscope, effectivePixelSize, null);
    }

    public double predict(
            String motionNoDwMrc, String microscope, double effectivePixelSize, String commandOverride)
            throws IOException, InterruptedException {
        Path mrc = Path.of(motionNoDwMrc.trim());
        if (!Files.isRegularFile(mrc)) {
            throw new IllegalStateException("motion no_dw MRC 不存在: " + mrc);
        }
        String command =
                StringUtils.hasText(commandOverride)
                        ? commandOverride.trim()
                        : commandRegistry.resolve(microscope);
        double rawDose = runContrastMean(command, mrc.toAbsolutePath().toString());
        if (effectivePixelSize <= 0) {
            throw new IllegalArgumentException("有效像素尺寸须大于 0: " + effectivePixelSize);
        }
        return DOSE_SCALE * rawDose / effectivePixelSize / effectivePixelSize;
    }

    private double runContrastMean(String command, String mrcPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command, mrcPath);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        log.info("执行 predict dose 命令: {} {}", command, mrcPath);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(commandTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(command + " 超时（" + commandTimeoutSeconds + "s）: " + mrcPath);
        }
        int code = process.exitValue();
        if (code != 0) {
            String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    command
                            + " 退出码 "
                            + code
                            + " "
                            + mrcPath
                            + (err.isBlank() ? "" : "\nstderr: " + err)
                            + (stdout.isBlank() ? "" : "\nstdout: " + stdout));
        }
        return parseRawDose(stdout);
    }

    private double parseRawDose(String output) {
        String trimmed = output.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("predict dose 命令无输出");
        }
        String[] tokens = trimmed.split("\\s+");
        String last = tokens[tokens.length - 1].trim();
        try {
            return Double.parseDouble(last);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("无法解析 predict dose 输出: " + output, e);
        }
    }
}
