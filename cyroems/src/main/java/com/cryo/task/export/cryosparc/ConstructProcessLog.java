/**
 * Copyright 2026 bejson.com
 */
package com.cryo.task.export.cryosparc;

import lombok.Data;

/**
 * Auto-generated: 2026-01-02 18:8:3
 *
 * @author bejson.com (i@bejson.com)
 * @website http://www.bejson.com/java2pojo/
 */
@Data
public class ConstructProcessLog
{

    private String type;
    private double ts;
    private String status;
    private int progress;
    private String message;


    public String getPercent() {
        return progress + "%";
    }

}