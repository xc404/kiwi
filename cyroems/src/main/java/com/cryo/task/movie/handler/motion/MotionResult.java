package com.cryo.task.movie.handler.motion;

import com.cryo.model.MrcFile;
import lombok.Data;

@Data
public class MotionResult
{
//    private String config_id;
//    private String movie_dataset_id;
//    private String task_id;
//    private String movie_id;

    private MrcFile dw;
    private MrcFile no_dw;
    private MrcFile dws;
    //    private String dw_png;
    private MotionFile local_motion;
    private MotionFile rigid_motion;
    private Double predict_dose;
    private String subtarctionOutput;

}
