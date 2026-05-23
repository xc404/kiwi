package com.kiwi.cryoems.bpm.mdoc.model;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * 与 cyroems {@code com.cryo.task.tilt.TiltMeta} 字段命名一一对齐，便于复用上游 mdoc 解析结果
 * 写入同一 Mongo 文档结构。
 */
@Data
@ToString
public class MdocTiltMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer ZValue;
    private Double TiltAngle;
    private double[] StagePosition;
    private Double StageZ;
    private Double Magnification;
    private Double Intensity;
    private Double ExposureDose;
    private Double PixelSpacing;
    private Integer SpotSize;
    private Double Defocus;
    private double[] ImageShift;
    private Double RotationAngle;
    private Double ExposureTime;
    private Integer Binning;
    private Integer MagIndex;
    private Double CountsPerElectron;
    private double[] MinMaxMean;
    private Double TargetDefocus;
    private Double PriorRecordDose;
    private String name;
    private Integer NumSubFrames;
    private double[] FrameDosesAndNumber;
    private String DateTime;
    private double[] FilterSlitAndLoss;
    private String ChannelName;
    private String CameraLength;
    private String dataId;
    private String motionResultId;
}
