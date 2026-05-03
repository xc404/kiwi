package com.cryo.task.tilt;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class RawTiltMeta
{
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
