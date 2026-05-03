package com.cryo.model;

import com.cryo.common.model.DataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class Gain extends DataEntity
{


    private String task_id;

    private String task_name;


    private GainConvertStatus gain_conversion_status;

    private GainConvertSoftware gain_conversion_software;

    private String gain_conversion_output_file;

    private String file_path;

    private String file_name;
    private boolean exported;




}
