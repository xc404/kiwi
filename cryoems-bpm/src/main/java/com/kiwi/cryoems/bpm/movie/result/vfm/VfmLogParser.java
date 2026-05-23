package com.kiwi.cryoems.bpm.movie.result.vfm;

import com.kiwi.cryoems.bpm.movie.model.vfm.VFMPoint;
import com.kiwi.cryoems.bpm.movie.model.vfm.VFMResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 对齐 cyroems {@code VFMSupport} 中对 {@code _predicted_boxes.txt} 的解析。
 */
@Component
public class VfmLogParser {

    public void parse(VFMResult vfmResult) {
        if (vfmResult == null || !StringUtils.hasText(vfmResult.getLogFile())) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(Path.of(vfmResult.getLogFile()), StandardCharsets.UTF_8);
            List<VFMPoint> points =
                    lines.stream()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty())
                            .map(VfmLogParser::parseLine)
                            .toList();
            vfmResult.setPointList(points);
        } catch (IOException e) {
            throw new IllegalStateException("读取 VFM 日志失败: " + vfmResult.getLogFile(), e);
        }
    }

    private static VFMPoint parseLine(String line) {
        String[] words = line.split("\\s+");
        if (words.length < 5) {
            throw new IllegalStateException("VFM 日志行格式异常: " + line);
        }
        double uMin = Double.parseDouble(words[0]);
        double vMin = Double.parseDouble(words[1]);
        double uMax = Double.parseDouble(words[2]);
        double vMax = Double.parseDouble(words[3]);
        double score = Double.parseDouble(words[4]);
        VFMPoint point = new VFMPoint();
        point.setScore(score);
        point.setU_min(uMin);
        point.setU_max(uMax);
        point.setV_min(vMin);
        point.setV_max(vMax);
        point.setRadius(Math.sqrt(Math.pow(uMax - uMin, 2) + Math.pow(vMax - vMin, 2)) / 2);
        point.setU_mean((uMin + uMax) / 2);
        point.setV_mean((vMin + vMax) / 2);
        return point;
    }
}
