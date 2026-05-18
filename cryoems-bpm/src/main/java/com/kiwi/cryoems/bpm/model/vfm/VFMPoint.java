package com.kiwi.cryoems.bpm.model.vfm;

import lombok.Data;

import java.io.Serializable;

@Data
public class VFMPoint implements Serializable
{

    private double u_min;
    private double u_max;
    private double u_mean;
    private double v_min;
    private double v_max;
    private double v_mean;
    private double radius;
    private double score;
}
