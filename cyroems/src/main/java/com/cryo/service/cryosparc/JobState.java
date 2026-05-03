/**
 * Copyright 2025 bejson.com
 */
package com.cryo.service.cryosparc;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.annotation.Transient;

import java.util.List;

/**
 * Auto-generated: 2025-12-30 12:2:7
 *
 * @author bejson.com (i@bejson.com)
 * @website http://www.bejson.com/java2pojo/
 */
@Data
public class JobState
{

    public static final int MaxLength = 30;
    private String task_id;
    private String status;
    private String cs_job_uid;
    private String error;
    private int retry_count;

    @Transient
    private List<JsonNode> results;

    private List<JsonNode> resultsView;
    private String resultMessage;


    public Boolean isFinished() {

        return "completed".equals(status) || "failed".equals(status) || "killed".equals(status);
    }

    public boolean isSuccess() {
        return "completed".equals(status) && StringUtils.isNotBlank(cs_job_uid);

    }


    public void setResults(List<JsonNode> results) {
        this.results = results;


        if(results != null && results.size() > MaxLength ){
           this.resultMessage = "Results truncated to first "+ MaxLength + " items of total " + results.size() + " items.";
            this.resultsView = results.subList(0, MaxLength);
            return;
        }
        this.resultsView = results;
    }
}