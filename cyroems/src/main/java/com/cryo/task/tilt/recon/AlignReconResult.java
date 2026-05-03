package com.cryo.task.tilt.recon;

import lombok.Data;

@Data
public class AlignReconResult
{
    private String xfproductOutput;
    private String patch2imodOutput;
    private String stack1Output;
    private String stack2Output;
    private String tiltOutput;
    private String binvolOutput;
    private String align_reconOutput;
    private String align_recon_x_y_view;
    private String align_recon_y_z_view;
    private String align_recon_x_z_view;
}
