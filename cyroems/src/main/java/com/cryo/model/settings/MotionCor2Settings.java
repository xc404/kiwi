package com.cryo.model.settings;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class MotionCor2Settings
{

    //        private String software;
    private Integer patch = 5;
    private Integer eer_sampling = 2;
    private Integer eer_fraction = 40;
    private Boolean save_as_float16 = false;
}
