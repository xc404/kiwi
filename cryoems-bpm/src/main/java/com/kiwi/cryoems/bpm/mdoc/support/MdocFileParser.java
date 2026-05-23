package com.kiwi.cryoems.bpm.mdoc.support;

import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocRawTiltMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.PropertyAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 IMOD/SerialEM 输出的 {@code *.mrc.mdoc} 文本文件，得到 {@link MdocMeta}。
 *
 * <p>纯解析逻辑组件，对齐 cyroems {@code com.cryo.task.tilt.parse.MDocParser}：</p>
 * <ul>
 *     <li>顶部 header 固定按行解析 {@code DataMode}（第 1 行）/ {@code ImageSize}（第 2 行）/
 *         {@code PixelSpacing}（第 4 行）/ {@code Voltage}（第 5 行）；</li>
 *     <li>第 9 行的注释行提取 {@code TiltAxisAngle} / {@code Binning} / {@code SpotSize}；</li>
 *     <li>遇到 {@code [ZValue = n]} 段切分 tilt，段内所有 {@code key = value} 收集到
 *         {@link MdocRawTiltMeta}，再二次转换为强类型 {@link MdocTiltMeta}。</li>
 * </ul>
 *
 * <p>本组件无状态，使用 JDK NIO + Spring {@link BeanWrapper} 实现，避免引入 hutool 等额外依赖；
 * 由 {@link com.kiwi.cryoems.bpm.mdoc.activity.MdocParser} 在 Camunda 流程内调用。</p>
 */
@Component
public class MdocFileParser {

    private static final int MIN_HEADER_LINES = 9;
    private static final int TILT_AXIS_HEADER_LINE_INDEX = 8;
    private static final int MIN_TILT_AXIS_TOKENS = 9;
    /** 第 9 行格式形如 {@code "[T = ... tiltAxis ... binning ... spot ..."}，去掉前缀 8 个字符。 */
    private static final int TILT_AXIS_HEADER_PREFIX_LEN = 8;

    public MdocMeta parse(File file) {
        if (file == null) {
            throw new IllegalArgumentException("mdoc 文件参数为空");
        }
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("mdoc 文件不存在: " + file.getAbsolutePath());
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            return parse(lines);
        } catch (IOException e) {
            throw new IllegalStateException("读取 mdoc 文件失败: " + file.getAbsolutePath(), e);
        }
    }

    public MdocMeta parse(List<String> lines) {
        if (lines == null || lines.size() < MIN_HEADER_LINES) {
            throw new IllegalArgumentException("mdoc 行数不足，无法解析元数据");
        }

        KeyValue dataMode = requireKeyValue(parseKeyValue(lines.get(0)), "DataMode");
        KeyValue imageSize = requireKeyValue(parseKeyValue(lines.get(1)), "ImageSize");
        KeyValue pixelSpacing = requireKeyValue(parseKeyValue(lines.get(3)), "PixelSpacing");
        KeyValue voltage = requireKeyValue(parseKeyValue(lines.get(4)), "Voltage");

        String tiltAxisHeaderLine = lines.get(TILT_AXIS_HEADER_LINE_INDEX);
        if (tiltAxisHeaderLine.length() < TILT_AXIS_HEADER_PREFIX_LEN + 1) {
            throw new IllegalArgumentException("第 9 行数据格式不正确，长度不足");
        }
        String tiltAxisHeaderBody =
                tiltAxisHeaderLine.substring(TILT_AXIS_HEADER_PREFIX_LEN, tiltAxisHeaderLine.length() - 1);
        String[] tiltAxisTokens = tiltAxisHeaderBody.split(" +");
        if (tiltAxisTokens.length < MIN_TILT_AXIS_TOKENS) {
            throw new IllegalArgumentException("第 9 行分割后的数据项数量不足");
        }
        String tiltAxisAngleRaw = tiltAxisTokens[2].trim();
        String binningRaw = tiltAxisTokens[5].trim();
        String spotSizeRaw = tiltAxisTokens[8].trim();

        List<MdocRawTiltMeta> rawTiltMetas = collectRawTilts(lines);

        MdocMeta meta = new MdocMeta();
        if (StringUtils.hasText(binningRaw)) {
            meta.setBinning(parseInt(binningRaw, "Binning"));
        }
        if (StringUtils.hasText(dataMode.value())) {
            meta.setDataMode(parseInt(dataMode.value(), "DataMode"));
        }
        if (StringUtils.hasText(imageSize.value())) {
            meta.setImageSize(parseDoubleArray(imageSize.value(), "ImageSize"));
        }
        if (StringUtils.hasText(pixelSpacing.value())) {
            meta.setPixelSpacing(parseDouble(pixelSpacing.value(), "PixelSpacing"));
        }
        if (StringUtils.hasText(spotSizeRaw)) {
            meta.setSpotSize(parseInt(spotSizeRaw, "SpotSize"));
        }
        // TODO: TiltAxisAngle 暂沿用 cyroems 历史硬编码（84.1°），后续需从 mdoc 解析真实值。
        meta.setTiltAxisAngle(84.1);
        if (StringUtils.hasText(voltage.value())) {
            meta.setVoltage(parseDouble(voltage.value(), "Voltage"));
        }
        meta.setRawTiltMetas(rawTiltMetas);
        meta.setTilts(rawTiltMetas.stream().map(this::convertToTilt).toList());

        if (StringUtils.hasText(tiltAxisAngleRaw)) {
            // tiltAxisAngleRaw 当前不写入 meta（沿用 cyroems 硬编码），保留以便后续启用。
        }
        return meta;
    }

    private List<MdocRawTiltMeta> collectRawTilts(List<String> lines) {
        List<MdocRawTiltMeta> rawTiltMetas = new ArrayList<>();
        Map<String, String> metaMap = new HashMap<>();
        int zValue = -1;

        for (String original : lines) {
            String line = original;
            if (line.startsWith("[ZValue")) {
                String stripped = line.substring(1, line.length() - 1);
                KeyValue zValuePair = requireKeyValue(parseKeyValue(stripped), "ZValue");
                zValue = parseInt(zValuePair.value(), "ZValue");
                if (zValue > 0) {
                    rawTiltMetas.add(buildRawTilt(metaMap));
                }
            }

            if (zValue < 0) {
                continue;
            }
            if (!line.contains("=")) {
                continue;
            }
            KeyValue kv = parseKeyValue(line);
            if (kv.key() != null && kv.value() != null) {
                metaMap.put(kv.key(), kv.value());
            }
        }
        if (zValue >= 0) {
            rawTiltMetas.add(buildRawTilt(metaMap));
        }
        return rawTiltMetas;
    }

    private MdocRawTiltMeta buildRawTilt(Map<String, String> metaMap) {
        MdocRawTiltMeta raw = new MdocRawTiltMeta();
        BeanWrapper wrapper = new BeanWrapperImpl(raw);
        for (Map.Entry<String, String> entry : metaMap.entrySet()) {
            if (!wrapper.isWritableProperty(entry.getKey())) {
                continue;
            }
            try {
                wrapper.setPropertyValue(entry.getKey(), entry.getValue());
            } catch (PropertyAccessException ignored) {
                // 兼容 mdoc 中可能出现的非约定键，忽略写入异常。
            }
        }
        metaMap.clear();
        return raw;
    }

    private MdocTiltMeta convertToTilt(MdocRawTiltMeta t) {
        MdocTiltMeta tilt = new MdocTiltMeta();
        if (StringUtils.hasText(t.getDefocus())) {
            tilt.setDefocus(Double.valueOf(t.getDefocus()));
        }
        if (StringUtils.hasText(t.getExposureDose()) && StringUtils.hasText(t.getExposureTime())) {
            // 与 cyroems 原实现保持一致：用 ExposureDose 是否存在作为是否设置 ExposureTime 的护栏。
            tilt.setExposureTime(Double.valueOf(t.getExposureTime()));
        }
        if (StringUtils.hasText(t.getIntensity())) {
            tilt.setIntensity(Double.valueOf(t.getIntensity()));
        }
        if (StringUtils.hasText(t.getMagnification())) {
            tilt.setMagnification(Double.valueOf(t.getMagnification()));
        }
        if (StringUtils.hasText(t.getZValue())) {
            tilt.setZValue(Integer.valueOf(t.getZValue()));
        }
        if (StringUtils.hasText(t.getStageZ())) {
            tilt.setStageZ(Double.valueOf(t.getStageZ()));
        }
        if (StringUtils.hasText(t.getStagePosition())) {
            tilt.setStagePosition(parseDoubleArray(t.getStagePosition(), "StagePosition"));
        }
        if (StringUtils.hasText(t.getImageShift())) {
            tilt.setImageShift(parseDoubleArray(t.getImageShift(), "ImageShift"));
        }
        if (StringUtils.hasText(t.getPixelSpacing())) {
            tilt.setPixelSpacing(Double.valueOf(t.getPixelSpacing()));
        }
        if (StringUtils.hasText(t.getTiltAngle())) {
            tilt.setTiltAngle(Double.valueOf(t.getTiltAngle()));
        }
        if (StringUtils.hasText(t.getSubFramePath())) {
            tilt.setName(extractFileName(t.getSubFramePath()));
        }
        if (StringUtils.hasText(t.getRotationAngle())) {
            tilt.setRotationAngle(Double.valueOf(t.getRotationAngle()));
        }
        if (StringUtils.hasText(t.getMagIndex())) {
            tilt.setMagIndex(Integer.valueOf(t.getMagIndex()));
        }
        if (StringUtils.hasText(t.getCountsPerElectron())) {
            tilt.setCountsPerElectron(Double.valueOf(t.getCountsPerElectron()));
        }
        if (StringUtils.hasText(t.getFilterSlitAndLoss())) {
            tilt.setFilterSlitAndLoss(parseDoubleArray(t.getFilterSlitAndLoss(), "FilterSlitAndLoss"));
        }
        if (StringUtils.hasText(t.getChannelName())) {
            tilt.setChannelName(t.getChannelName());
        }
        if (StringUtils.hasText(t.getCameraLength())) {
            tilt.setCameraLength(t.getCameraLength());
        }
        if (StringUtils.hasText(t.getSpotSize())) {
            tilt.setSpotSize(Integer.valueOf(t.getSpotSize()));
        }
        tilt.setDateTime(t.getDateTime());
        if (StringUtils.hasText(t.getNumSubFrames())) {
            tilt.setNumSubFrames(Integer.valueOf(t.getNumSubFrames()));
        }
        if (StringUtils.hasText(t.getFrameDosesAndNumber())) {
            tilt.setFrameDosesAndNumber(parseDoubleArray(t.getFrameDosesAndNumber(), "FrameDosesAndNumber"));
        }
        if (StringUtils.hasText(t.getExposureDose())) {
            tilt.setExposureDose(Double.valueOf(t.getExposureDose()));
        }
        if (StringUtils.hasText(t.getTargetDefocus())) {
            tilt.setTargetDefocus(Double.valueOf(t.getTargetDefocus()));
        }
        if (StringUtils.hasText(t.getPriorRecordDose())) {
            tilt.setPriorRecordDose(Double.valueOf(t.getPriorRecordDose()));
        }
        return tilt;
    }

    private static String extractFileName(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private static KeyValue requireKeyValue(KeyValue kv, String fieldName) {
        if (kv == null || kv.key() == null || kv.value() == null) {
            throw new IllegalArgumentException(fieldName + " 解析结果无效");
        }
        return kv;
    }

    private static int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(fieldName + " 数值格式错误: " + value);
        }
    }

    private static double parseDouble(String value, String fieldName) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(fieldName + " 数值格式错误: " + value);
        }
    }

    private static double[] parseDoubleArray(String value, String fieldName) {
        try {
            return Arrays.stream(value.trim().split(" +"))
                    .mapToDouble(Double::parseDouble)
                    .toArray();
        } catch (NumberFormatException e) {
            throw new NumberFormatException(fieldName + " 数组解析失败: " + value);
        }
    }

    /**
     * 解析 {@code key = value} 形式的一行，缺失等号时返回空 record。
     * 提供为 package-private 静态方法，便于单元测试覆盖。
     */
    static KeyValue parseKeyValue(String line) {
        if (line == null || !line.contains("=")) {
            return new KeyValue(null, null);
        }
        int idx = line.indexOf('=');
        String key = line.substring(0, idx).trim();
        String value = line.substring(idx + 1).trim();
        return new KeyValue(key.isEmpty() ? null : key, value.isEmpty() ? null : value);
    }

    /** 内部使用的简易 {@code key/value} 载体，避免引入 hutool / commons-lang3 的 Pair。 */
    record KeyValue(String key, String value) {}
}
