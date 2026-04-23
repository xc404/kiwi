package com.cryo.task.movie.handler.vfm;

import lombok.Data;

import java.util.List;

@Data
public class VFMResult
{
    private String logFile;
    private String outputFile;
    private String pngFile;

    private List<VFMPoint> pointList;
}
