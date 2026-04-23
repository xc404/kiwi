package com.cryo.model.settings;

import lombok.Data;

import java.util.Objects;
import java.util.Optional;

@Data
public class TaskSettings
{
    private String dataset_id;
    private Double acceleration_kv;
    private Double spherical_aberration;
    private Double amplitude_contrast;
    private Integer binning_factor;

    private MotionCorrectionSettings motion_correction_settings;
    private CtfEstimationSettings ctf_estimation_settings;
    private TaskDataSetSetting taskDataSetSetting;
    private ETSettings etSettings;
    private CryosparcSettings cryosparcSettings;
    private ExportSettings exportSettings;

    public boolean settingsEquals(Object o) {
        if( !(o instanceof TaskSettings that) ) {
            return false;
        }
        return Objects.equals(getAcceleration_kv(), that.getAcceleration_kv()) && Objects.equals(getSpherical_aberration(), that.getSpherical_aberration()) && Objects.equals(getAmplitude_contrast(), that.getAmplitude_contrast()) && Objects.equals(getBinning_factor(), that.getBinning_factor()) && Objects.equals(getMotion_correction_settings(), that.getMotion_correction_settings()) && Objects.equals(getCtf_estimation_settings(), that.getCtf_estimation_settings());
    }

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof TaskSettings that) ) {
            return false;
        }
        return Objects.equals(getAcceleration_kv(), that.getAcceleration_kv()) && Objects.equals(getSpherical_aberration(), that.getSpherical_aberration()) && Objects.equals(getAmplitude_contrast(), that.getAmplitude_contrast()) && Objects.equals(getBinning_factor(), that.getBinning_factor()) && Objects.equals(getMotion_correction_settings(), that.getMotion_correction_settings()) && Objects.equals(getCtf_estimation_settings(), that.getCtf_estimation_settings()) && Objects.equals(getTaskDataSetSetting(), that.getTaskDataSetSetting());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAcceleration_kv(), getSpherical_aberration(), getAmplitude_contrast(), getBinning_factor(), getMotion_correction_settings(), getCtf_estimation_settings(), getTaskDataSetSetting());
    }


    public ETSettings getEtSettings() {
        return Optional.ofNullable(this.etSettings).orElse(ETSettings.defaultETSettings);
    }


    public CryosparcSettings getCryosparcSettings() {
        return Optional.ofNullable(this.cryosparcSettings).orElse(new CryosparcSettings());
    }

}
