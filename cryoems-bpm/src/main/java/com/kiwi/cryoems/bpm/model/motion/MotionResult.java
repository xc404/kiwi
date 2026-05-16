package com.kiwi.cryoems.bpm.model.motion;

import lombok.Data;

@Data
public class MotionResult {

    private MrcFile dw;
    private MrcFile no_dw;
    private MrcFile dws;
    private MotionFile local_motion;
    private MotionFile rigid_motion;
    private Double predict_dose;
    private String subtarctionOutput;
}
