package com.kiwi.cryoems.bpm.movie.result.ctf;

import com.kiwi.cryoems.bpm.movie.model.ctf.EstimationResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 对齐 cyroems {@code Ctffind5Support#parseLogFile}。
 */
@Component
public class Ctffind5LogParser {

    public void parse(EstimationResult estimationResult) {
        if (estimationResult == null || estimationResult.getLogFile() == null || estimationResult.getLogFile().isBlank()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(Path.of(estimationResult.getLogFile()), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                throw new IllegalStateException("CTFFIND 日志为空: " + estimationResult.getLogFile());
            }
            String line = lines.get(lines.size() - 1);
            String[] split = line.split(" +");
            if (split.length < 10) {
                throw new IllegalStateException("CTFFIND 日志行格式异常: " + line);
            }
            estimationResult.setMicrograph_number(Double.parseDouble(split[0]));
            estimationResult.setDefocus_1(Double.parseDouble(split[1]));
            estimationResult.setDefocus_2(Double.parseDouble(split[2]));
            estimationResult.setAzimuth_of_astigmatism(Double.parseDouble(split[3]));
            estimationResult.setAdditional_phase_shift(Double.parseDouble(split[4]));
            estimationResult.setCross_correlation(Double.parseDouble(split[5]));
            estimationResult.setSpacing(parseDouble(split[6]));
            estimationResult.setEstimated_tilt_axis_angle(Double.parseDouble(split[7]));
            estimationResult.setEstimated_tilt_angle(Double.parseDouble(split[8]));
            estimationResult.setEstimated_sample_thickness(Double.parseDouble(split[9]));
        } catch (IOException e) {
            throw new IllegalStateException("读取 CTFFIND 日志失败: " + estimationResult.getLogFile(), e);
        }
    }

    private static Double parseDouble(String txt) {
        if (txt == null || txt.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(txt.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
