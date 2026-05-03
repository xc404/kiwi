package com.cryo.task.tilt.parse;

import cn.hutool.core.bean.copier.BeanCopier;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Pair;
import com.cryo.task.tilt.MDocMeta;
import com.cryo.task.tilt.RawTiltMeta;
import com.cryo.task.tilt.TiltMeta;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//@Service
public class MDocParser
{

    public MDocMeta parse(File file) {
        try {
            List<String> lines = FileUtils.readLines(file, "UTF-8");

            // 验证文件行数是否足够
            if( lines.size() < 9 ) {
                throw new IllegalArgumentException("文件行数不足，无法解析MDoc元数据");
            }

            Pair<String, String> dataMode = validateParseResult(parseValue(lines.get(0)), "DataMode");
            Pair<String, String> imageSize = validateParseResult(parseValue(lines.get(1)), "ImageSize");
            Pair<String, String> pixelSpacing = validateParseResult(parseValue(lines.get(3)), "PixelSpacing");
            Pair<String, String> voltage = validateParseResult(parseValue(lines.get(4)), "Voltage");

            String t1 = lines.get(8);
            // 验证字符串长度是否足够进行substring操作
            if( t1.length() < 9 ) {
                throw new IllegalArgumentException("第9行数据格式不正确，长度不足");
            }
            String substring = t1.substring(8, t1.length() - 1);
            String[] split = substring.split(" +");
            if( split.length < 9 ) {
                throw new IllegalArgumentException("第9行分割后的数据项数量不足");
            }
            String tiltAxisAngle = StringUtils.trim(split[2]);
            String binning = StringUtils.trim(split[5]);
            String spotSize = StringUtils.trim(split[8]);

            List<RawTiltMeta> rawTiltMetas = new ArrayList<>();
            Map<String, String> metaMap = new HashMap<>();
            int zValue = -1;

            for( String line : lines ) {
                if( line.startsWith("[ZValue") ) {
                    line = line.substring(1, line.length() - 1);
                    Pair<String, String> zValuePair = validateParseResult(parseValue(line), "ZValue");
                    zValue = parseIntValue(zValuePair.getValue(), "ZValue");

                    if( zValue > 0 ) {
                        rawTiltMetas.add(covertMeta(metaMap));
                    }
                }

                if( zValue < 0 ) {
                    continue;
                }
                if( !line.contains("=") ) {
                    continue;
                }

                Pair<String, String> parseValue = parseValue(line);
                if( parseValue.getKey() != null && parseValue.getValue() != null ) {
                    metaMap.put(parseValue.getKey(), parseValue.getValue());
                }
            }

            if( zValue >= 0 ) {
                rawTiltMetas.add(covertMeta(metaMap));
            }

            MDocMeta mDocMeta = new MDocMeta();
            if( StringUtils.isNotBlank(binning) ) {
                mDocMeta.setBinning(parseIntValue(binning, "Binning"));
            }
            if( StringUtils.isNotBlank(dataMode.getValue()) ) {

                mDocMeta.setDataMode(parseIntValue(dataMode.getValue(), "DataMode"));
            }
            if( StringUtils.isNotBlank(imageSize.getValue()) ) {

                mDocMeta.setImageSize(parseDoubleArray(imageSize.getValue(), "ImageSize"));
            }
            if( StringUtils.isNotBlank(pixelSpacing.getValue()) ) {

                mDocMeta.setPixelSpacing(parseDoubleValue(pixelSpacing.getValue(), "PixelSpacing"));
            }
            if( StringUtils.isNotBlank(spotSize) ) {
                mDocMeta.setSpotSize(parseIntValue(spotSize, "SpotSize"));
            }
            mDocMeta.setTiltAxisAngle(84.1); // todo change read from file
            if( StringUtils.isNotBlank(voltage.getValue()) ) {
                mDocMeta.setVoltage(parseDoubleValue(voltage.getValue(), "Voltage"));
            }
            mDocMeta.setRawTiltMetas(rawTiltMetas);

            List<TiltMeta> tiltMetas = rawTiltMetas.stream()
                    .map(this::convertToTilt)
                    .toList();
            mDocMeta.setTilts(tiltMetas);

            return mDocMeta;
        } catch( IOException e ) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        } catch( NumberFormatException e ) {
            throw new RuntimeException("数值解析失败: " + e.getMessage(), e);
        } catch( IllegalArgumentException e ) {
            throw new RuntimeException("参数验证失败: " + e.getMessage(), e);
        }
    }

    private Pair<String, String> validateParseResult(Pair<String, String> result, String fieldName) {
        if( result == null || result.getKey() == null || result.getValue() == null ) {
            throw new IllegalArgumentException(fieldName + " 解析结果无效");
        }
        return result;
    }

    private int parseIntValue(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch( NumberFormatException e ) {
            throw new NumberFormatException(fieldName + " 数值格式错误: " + value);
        }
    }

    private double parseDoubleValue(String value, String fieldName) {
        try {
            return Double.parseDouble(value);
        } catch( NumberFormatException e ) {
            throw new NumberFormatException(fieldName + " 数值格式错误: " + value);
        }
    }

    private double[] parseDoubleArray(String value, String fieldName) {
        try {
            return Arrays.stream(value.split(" +"))
                    .mapToDouble(Double::parseDouble)
                    .toArray();
        } catch( NumberFormatException e ) {
            throw new NumberFormatException(fieldName + " 数组解析失败: " + value);
        }
    }


    private RawTiltMeta covertMeta(Map<String, String> metaMap) {
        RawTiltMeta rawTiltMeta = new RawTiltMeta();
        BeanCopier<RawTiltMeta> beanCopier = BeanCopier.create(metaMap, rawTiltMeta, CopyOptions.create());
        beanCopier.copy();
        metaMap.clear();
        return rawTiltMeta;
    }

    private TiltMeta convertToTilt(RawTiltMeta t) {
        TiltMeta tiltMeta = new TiltMeta();
        if( StringUtils.isNotBlank(t.getDefocus()) ) {
            tiltMeta.setDefocus(Double.valueOf(t.getDefocus()));
        }
        if( StringUtils.isNotBlank(t.getExposureDose()) ) {

            tiltMeta.setExposureTime(Double.valueOf(t.getExposureTime()));
        }
        if( StringUtils.isNotBlank(t.getIntensity()) ) {

            tiltMeta.setIntensity(Double.valueOf(t.getIntensity()));
        }
        if( StringUtils.isNotBlank(t.getMagnification()) ) {

            tiltMeta.setMagnification(Double.valueOf(t.getMagnification()));
        }
        if( StringUtils.isNotBlank(t.getZValue()) ) {

            tiltMeta.setZValue(Integer.valueOf(t.getZValue()));
        }
        if( StringUtils.isNotBlank(t.getStageZ()) ) {
            tiltMeta.setStageZ(Double.valueOf(t.getStageZ()));
        }
        if( StringUtils.isNotBlank(t.getStagePosition()) ) {

            tiltMeta.setStagePosition(Arrays.stream(t.getStagePosition().split(" +")).mapToDouble(Double::valueOf).toArray());
        }
        if( StringUtils.isNotBlank(t.getImageShift()) ) {

            tiltMeta.setImageShift(Arrays.stream(t.getImageShift().split(" +")).mapToDouble(Double::valueOf).toArray());
        }
        if( StringUtils.isNotBlank(t.getPixelSpacing()) ) {
            tiltMeta.setPixelSpacing(Double.valueOf(t.getPixelSpacing()));
        }
//        if( StringUtils.isNotBlank(t.getMinMaxMean()) ) {
//            tiltMeta.setMinMaxMean(Arrays.stream(t.getMinMaxMean().split(" +")).mapToDouble(Double::valueOf).toArray());
//        }
//        if( StringUtils.isNotBlank(t.getExposureTime()) ) {
//            tiltMeta.setExposureTime(Double.valueOf(t.getExposureTime()));
//        }
        if( StringUtils.isNotBlank(t.getTiltAngle()) ) {
            tiltMeta.setTiltAngle(Double.valueOf(t.getTiltAngle()));
        }
        if( StringUtils.isNotBlank(t.getSubFramePath()) ) {
            tiltMeta.setName(FilenameUtils.getName(t.getSubFramePath()));
        }
        if( StringUtils.isNotBlank(t.getRotationAngle()) ) {
            tiltMeta.setRotationAngle(Double.valueOf(t.getRotationAngle()));
        }
//        if( StringUtils.isNotBlank(t.getBinning()) ) {
//            tiltMeta.setBinning(Integer.valueOf(t.getBinning()));
//        }
        if( StringUtils.isNotBlank(t.getMagIndex()) ) {
            tiltMeta.setMagIndex(Integer.valueOf(t.getMagIndex()));
        }
        if( StringUtils.isNotBlank(t.getCountsPerElectron()) ) {
            tiltMeta.setCountsPerElectron(Double.valueOf(t.getCountsPerElectron()));
        }
        if( StringUtils.isNotBlank(t.getFilterSlitAndLoss()) ) {
            tiltMeta.setFilterSlitAndLoss(Arrays.stream(t.getFilterSlitAndLoss().split(" +")).mapToDouble(Double::valueOf).toArray());
        }
        if( StringUtils.isNotBlank(t.getChannelName()) ) {
            tiltMeta.setChannelName(t.getChannelName());
        }
        if( StringUtils.isNotBlank(t.getCameraLength()) ) {
            tiltMeta.setCameraLength(t.getCameraLength());
        }

        if( StringUtils.isNotBlank(t.getSpotSize()) ) {
            tiltMeta.setSpotSize(Integer.valueOf(t.getSpotSize()));
        }
        tiltMeta.setDateTime(t.getDateTime());
        if( StringUtils.isNotBlank(t.getStagePosition()) ) {

            tiltMeta.setStagePosition(Arrays.stream(t.getStagePosition().split(" +")).mapToDouble(Double::valueOf).toArray());
        }
        if( StringUtils.isNotBlank(t.getNumSubFrames()) ) {
            tiltMeta.setNumSubFrames(Integer.valueOf(t.getNumSubFrames()));
        }

        if( StringUtils.isNotBlank(t.getFrameDosesAndNumber()) ) {
            tiltMeta.setFrameDosesAndNumber(Arrays.stream(t.getFrameDosesAndNumber().split(" +")).mapToDouble(Double::valueOf).toArray());
        }
        if( StringUtils.isNotBlank(t.getExposureDose()) ) {
            tiltMeta.setExposureDose(Double.valueOf(t.getExposureDose()));
        }
        if( StringUtils.isNotBlank(t.getTargetDefocus()) ) {

            tiltMeta.setTargetDefocus(Double.valueOf(t.getTargetDefocus()));
        }
        if( StringUtils.isNotBlank(t.getPriorRecordDose()) ) {
            tiltMeta.setPriorRecordDose(Double.valueOf(t.getPriorRecordDose()));
        }

        return tiltMeta;
    }

    public static Pair<String, String> parseValue(String line) {
        if( !line.contains("=") ) {
            return Pair.of(null, null);
        }
        String[] split = line.split("=");
        return Pair.of(StringUtils.trim(split[0]), StringUtils.trim(split[1]));
    }

    public static void main(String[] args) {
        MDocParser mdocParser = new MDocParser();
        String file = "E:\\Projects\\cryo-em-server-backend-main-2\\doc\\Position_1_2.mdoc";
        MDocMeta parse = mdocParser.parse(new File(file));
        System.out.println(parse.getTilts().size());
    }
}
