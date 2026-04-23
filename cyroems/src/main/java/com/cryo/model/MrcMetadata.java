package com.cryo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
@Data
public class MrcMetadata
{
    private int columns;
    private int rows;
    private int sections;
    private int mode;
    private String mode_name;
    private double minimum_density;
    private double maximum_density;
    private double mean_density;
    private Double vmax;
    private Double vmin;

    private String file;
}