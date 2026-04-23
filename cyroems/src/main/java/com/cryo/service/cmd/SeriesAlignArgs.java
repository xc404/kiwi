package com.cryo.service.cmd;

import lombok.Data;

@Data
public class SeriesAlignArgs
{
    private String mrc_file;
    private String model_file;
    private String tilt_path;
    private String tilt_axis_angle;
    private String pixel_size;
    private String max_avg;
}
