package com.cryo.task.tilt;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class TiltMeta
{
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
