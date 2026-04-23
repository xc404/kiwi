package com.cryo.model.settings;

import lombok.Data;

import java.util.Objects;

@Data
public class CtfEstimationSettings
{
    private String software;
    private Ctffind4Settings ctffind4;
    private Ctffind5Settings ctffind5;

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof CtfEstimationSettings that) ) {
            return false;
        }
        if( "ctffind4".equalsIgnoreCase(software) ) {
            return Objects.equals(ctffind4, that.getCtffind4());
        }
        return Objects.equals(ctffind5, that.getCtffind5());
    }

    @Override
    public int hashCode() {
        if( "ctffind4".equalsIgnoreCase(software) ) {
            return ctffind4.hashCode();
        }
        return ctffind5.hashCode();
    }
}
