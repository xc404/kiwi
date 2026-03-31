package com.kiwi.bpmn.component.slurm;

import com.kiwi.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlurmJob extends BaseEntity<String>
{
    private String jobId;
    private String jobName;
    private String sbatchFilePath;
    private String outputFilePath;
    private String errorFilePath;

}
