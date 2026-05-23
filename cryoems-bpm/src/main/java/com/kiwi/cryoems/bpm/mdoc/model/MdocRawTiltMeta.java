package com.kiwi.cryoems.bpm.mdoc.model;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * 与 cyroems {@code com.cryo.task.tilt.RawTiltMeta} 字段命名一一对齐，便于直接复用
 * 上游 mdoc 文件解析逻辑与现有 Mongo 文档结构。
 *
 * <p>字段保持 PascalCase 以匹配 mdoc 中 {@code key = value} 的原始键名（如 {@code TiltAngle}），
 * 解析时按字符串完整透传，再由 {@link com.kiwi.cryoems.bpm.mdoc.support.MdocFileParser} 二次转换为
 * {@link MdocTiltMeta} 强类型表示。</p>
 */
@Data
@ToString
public class MdocRawTiltMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ZValue;
    private String TiltAngle;
    private String StagePosition;
    private String StageZ;
    private String Magnification;
    private String Intensity;
    private String ExposureDose;
    private String PixelSpacing;
    private String SpotSize;
    private String Defocus;
    private String ImageShift;
    private String RotationAngle;
    private String ExposureTime;
    private String Binning;
    private String MagIndex;
    private String CountsPerElectron;
    private String MinMaxMean;
    private String TargetDefocus;
    private String PriorRecordDose;
    private String SubFramePath;
    private String NumSubFrames;
    private String FrameDosesAndNumber;
    private String DateTime;
    private String FilterSlitAndLoss;
    private String ChannelName;
    private String CameraLength;
}
