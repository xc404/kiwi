package com.cryo.model.settings;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class Ctffind5Settings
{

    private Integer spectrum_size;
    private Integer min_res;
    private Integer max_res;
    private Integer min_defocus;
    private Integer max_defocus;
    private Integer defocus_step;
}
