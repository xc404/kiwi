package com.cryo.service.vfm;

import lombok.Data;

@Data
public class VFMParams
{
    private double df1;
    private double df2;
    private double dfang;
    private double vol_kv;
    private double cs_mm;
    private double w;
    private double phase_shift;
    private double psize_in;
    private boolean picking = true;
    private boolean cryosparc;
}
