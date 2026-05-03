package com.kiwi.cryoems.bpm.model;

import java.io.Serializable;

/**
 * 与 IMOD {@code header} 文本输出解析结果对齐的流程变量载体（字段语义同 cyroems {@code com.cryo.model.MrcMetadata}）。
 */
public class MrcMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private int columns;
    private int rows;
    private int sections;
    private int mode;
    private String modeName;
    private double minimumDensity;
    private double maximumDensity;
    private double meanDensity;
    private Double vmax;
    private Double vmin;
    private String file;

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        this.columns = columns;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public int getSections() {
        return sections;
    }

    public void setSections(int sections) {
        this.sections = sections;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String getModeName() {
        return modeName;
    }

    public void setModeName(String modeName) {
        this.modeName = modeName;
    }

    public double getMinimumDensity() {
        return minimumDensity;
    }

    public void setMinimumDensity(double minimumDensity) {
        this.minimumDensity = minimumDensity;
    }

    public double getMaximumDensity() {
        return maximumDensity;
    }

    public void setMaximumDensity(double maximumDensity) {
        this.maximumDensity = maximumDensity;
    }

    public double getMeanDensity() {
        return meanDensity;
    }

    public void setMeanDensity(double meanDensity) {
        this.meanDensity = meanDensity;
    }

    public Double getVmax() {
        return vmax;
    }

    public void setVmax(Double vmax) {
        this.vmax = vmax;
    }

    public Double getVmin() {
        return vmin;
    }

    public void setVmin(Double vmin) {
        this.vmin = vmin;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }
}
