package com.cryo.task.tilt;

import lombok.Data;

import java.util.List;

@Data
public class MDocMeta
{
    private Integer dataMode;
    private double[] imageSize;
    private String imageFile;
    private Double pixelSpacing;
    private String tomography;
    private Double tiltAxisAngle;
    private Integer binning;
    private Integer spotSize;
    private Double voltage;
    private List<RawTiltMeta> rawTiltMetas;
    private List<TiltMeta> tilts;
}
