package com.cryo.model;

import com.cryo.service.cmd.SoftwareExe;
import lombok.Data;

import java.util.Comparator;
import java.util.Map;

@Data
public class MicroscopeConfig
{
    private String microscope;
    private String root_path;
    private MicroscopeTEMstigma microscope_temstigma;
    private Map<String, Scale> scales;
    private SoftwareExe predict_dose;

    public Scale getClosetScale(double pixel_size) {
        if( this.scales == null ) {
            return null;
        }
        String key = this.scales.keySet().stream()
                .min(Comparator.comparingDouble(o -> Math.abs(Double.parseDouble(o) - pixel_size)))
                .get();
        return scales.get(key);
    }

    public SoftwareExe getPredict_dose() {
        if( predict_dose != null ) {
            return predict_dose;
        }
        switch( this.microscope ) {
            case "Titan1_k3":
                predict_dose = SoftwareExe.Titan1Mean;
                break;
            case "Titan2_k3":
                predict_dose = SoftwareExe.Titan2Mean;
                break;
            case "Titan3_falcon":
                predict_dose = SoftwareExe.Titan3Mean;
                break;
            default:
                throw new IllegalArgumentException("unsupported microscope");
        }
        return predict_dose;
    }

    @Data
    public static class MicroscopeTEMstigma
    {
        private double x_temstigma_step;
        private double y_temstigma_step;
        private double y_temstigma_angle;
        private double x_temstigma_angle;
    }

    @Data
    public static class Scale
    {
        private double major_scale;
        private double minor_scale;
        private double distort_ang;
    }
}
