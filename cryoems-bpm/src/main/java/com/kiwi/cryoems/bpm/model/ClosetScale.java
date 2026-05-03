package com.kiwi.cryoems.bpm.model;

import java.io.Serializable;

/**
 * 与 cyroems {@code MicroscopeConfig.Scale} 一致：在 {@code p_size} 邻近档位中选最近一条畸变标定。
 */
public class ClosetScale implements Serializable {

    private static final long serialVersionUID = 1L;

    private double majorScale;
    private double minorScale;
    private double distortAng;
    /** 与 {@code p_size} 最接近的配置键（像素尺寸，字符串形式）。 */
    private String matchedPixelSizeKey;

    public double getMajorScale() {
        return majorScale;
    }

    public void setMajorScale(double majorScale) {
        this.majorScale = majorScale;
    }

    public double getMinorScale() {
        return minorScale;
    }

    public void setMinorScale(double minorScale) {
        this.minorScale = minorScale;
    }

    public double getDistortAng() {
        return distortAng;
    }

    public void setDistortAng(double distortAng) {
        this.distortAng = distortAng;
    }

    public String getMatchedPixelSizeKey() {
        return matchedPixelSizeKey;
    }

    public void setMatchedPixelSizeKey(String matchedPixelSizeKey) {
        this.matchedPixelSizeKey = matchedPixelSizeKey;
    }
}
