package com.cryo.task.movie.handler.ctf;

import lombok.Data;

//# Columns: #1 - micrograph number; #2 - defocus 1 [Angstroms]; #3 - defocus 2; #4 - azimuth of astigmatism;
// #5 - additional phase shift [radians]; #6 - cross correlation; #7 - spacing (in Angstroms) up to which CTF rings were fit successfully;
// #8 - Estimated tilt axis angle; #9 - Estimated tilt angle ; #10 Estimated sample thickness (in Angstroms)

@Data
public class EstimationResult
{
    private Double micrograph_number;
    private Double defocus_1;
    private Double defocus_2;
    private Double azimuth_of_astigmatism;
    private Double additional_phase_shift;
    private Double cross_correlation;
    private Double spacing;
    private Double estimated_tilt_axis_angle;
    private Double estimated_tilt_angle;
    private Double estimated_sample_thickness;
    private Double stigma_x;
    private Double stigma_y;
//    private double predict_dose;
    private String outputFile;
    private String logFile;
    private String avrotFile;


}
