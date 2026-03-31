package com.kiwi.bpmn.component.slurm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kiwi.bpm.slurm")
@Data
public class SlurmProperties
{
    private String workDirectory;
    private int threadPoolSize = 5;
}
