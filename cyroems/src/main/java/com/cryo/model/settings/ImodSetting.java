package com.cryo.model.settings;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.Data;

@Data
public class ImodSetting
{
    @Hidden
    private Double tiltxcorr_sigma1 = 0.03;
    @Hidden
    private Double tiltxcorr_radius2 = 0.25 ;
    @Hidden
    private Double tiltxcorr_sigma2 = 0.05;
    @Hidden
    private Integer tiltxcorr_it = 4;
    @Hidden
    private Integer tiltxcorr_im = 4;
    @Hidden
    private Double tiltxcorr_overlap = 0.33;

    @Hidden
    private Integer newstack_bin = 4;
    @Hidden
    private Integer newstack_mo = 0;
    @Hidden
    private Integer newstack_fl = 2;
    @Hidden
    private Integer newstack_im = 1;

    @Hidden
    private Integer imodchopconts_overlap = 4;
    @Hidden
    private Integer imodchopconts_s = 1;




    private Integer border_size =46;
    private Integer patch_size = 150;
    private Double error_threshold = 5.0;
}
