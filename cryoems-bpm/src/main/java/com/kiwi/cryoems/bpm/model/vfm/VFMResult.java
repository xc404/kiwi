package com.kiwi.cryoems.bpm.model.vfm;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class VFMResult implements Serializable
{

    private String logFile;
    private String outputFile;
    private String pngFile;
    private List<VFMPoint> pointList;
}
