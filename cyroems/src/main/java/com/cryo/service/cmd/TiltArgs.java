package com.cryo.service.cmd;

import lombok.Data;

@Data
public class TiltArgs
{
    private String mrc_file;
    private String model_file;
    private String tilt_path;
    private String xtilt;
}
