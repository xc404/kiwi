package com.kiwi.cryoems.bpm.movie.model.ctf;

import lombok.Data;

import java.io.Serializable;

@Data
public class EstimationResult implements Serializable
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
    private String outputFile;
    private String logFile;
    private String avrotFile;
}
