package com.cryo.task.tilt.seriesalign;

import lombok.Data;

@Data
public class SeriesAlignResult
{
    private String modelFileOutput;
    private String residualFileOutput;
    private String fidXYZOutput;
    private String tiltFileOutput;
    private String xAxisTiltOutput;
    private String transformOutput;
    private String filledInModelOutput;
}
