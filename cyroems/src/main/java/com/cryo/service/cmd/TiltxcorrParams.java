package com.cryo.service.cmd;

import lombok.Data;

@Data
public class TiltxcorrParams
{
    private String tilt_axis_angle;
    private String tilt_path;
    private String sigma1;
    private String radius2;
    private String sigma2;
    private String patch_size;
    private String overlap;
    private String prexf;
    private String border;
    private String it;
    private String im;

}
