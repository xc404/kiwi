package com.cryo.task.movie.handler.vfm;

import lombok.Data;

@Data
public class VFMPoint
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
