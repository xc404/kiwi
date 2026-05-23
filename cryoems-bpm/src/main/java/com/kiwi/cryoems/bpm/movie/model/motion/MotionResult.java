package com.kiwi.cryoems.bpm.movie.model.motion;

import lombok.Data;

import java.io.Serializable;

@Data
public class MotionResult implements Serializable
{

    private MrcFile dw;
    private MrcFile no_dw;
    private MrcFile dws;
    private MotionFile local_motion;
    private MotionFile rigid_motion;
    private Double predict_dose;
    private String subtarctionOutput;
}
