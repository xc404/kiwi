package com.cryo.model.settings;

import lombok.Data;

import java.util.Objects;

@Data
public class MotionCorrectionSettings
{
    private String software;
    private MotionCor2Settings motionCor2;
    private MotionCor3Settings motionCor3;

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof MotionCorrectionSettings that) ) {
            return false;
        }
        if(!Objects.equals(software, that.getSoftware()) ){
            return false;
        }
        if( software.equalsIgnoreCase("motioncor2") ) {
            return motionCor2.equals(that.getMotionCor2());
        }
        if( software.equalsIgnoreCase("motioncor3") ) {
            return motionCor3.equals(that.getMotionCor3());
        }
        return false;
    }

    @Override
    public int hashCode() {
        if( "motioncor2".equalsIgnoreCase(software) ) {
            return motionCor2.hashCode();
        }
        return motionCor3.hashCode();
    }
}
