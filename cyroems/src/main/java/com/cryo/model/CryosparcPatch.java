package com.cryo.model;

import com.cryo.common.model.DataEntity;
import com.cryo.service.cryosparc.CryosparcJob;
import com.cryo.service.cryosparc.JobType;
import com.cryo.task.export.cryosparc.CryosparcResult;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class CryosparcPatch extends DataEntity
{
    private CryosparcResult cryosparcResult;
    private String task_id;
    private String pattern;
    private List<String> movies;
    private String error;
    private String status;
    private String exportTaskId;
    Map<JobType, CryosparcJob> cryosparcJobMap;

    public void addCryojob(JobType jobType, CryosparcJob cryosparcJob) {
        if( cryosparcJobMap == null ) {
            cryosparcJobMap = new HashMap<>();
        }
        cryosparcJobMap.put(jobType, cryosparcJob);
    }
}
