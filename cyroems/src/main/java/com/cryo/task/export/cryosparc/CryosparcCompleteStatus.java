package com.cryo.task.export.cryosparc;

import com.cryo.service.cryosparc.CryosparcJob;
import com.cryo.service.cryosparc.JobType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class CryosparcCompleteStatus
{


    private boolean write_star_status;
    @JsonIgnore
    private Map<JobType, CryosparcJob> cryosparcJobs;
    private boolean autoAttachStatus;

    public void addJob(JobType jobType, CryosparcJob cryosparcJob) {
        if( this.cryosparcJobs == null ) {
            this.cryosparcJobs = new HashMap<>();
        }
        this.cryosparcJobs.put(jobType, cryosparcJob);
    }


    public enum Status
    {
        Processing,
        Success,
        Failed,
    }


    private Status status;
    private String message;
    private String outputPath;
    private Date startTime;
    private Date endTime;
    private ConstructProcessLog summary;

    private boolean import_particles_status;
    private boolean extract_micrographs_status;
    private boolean classification_status;
    private boolean copy_to_user_status;

}
