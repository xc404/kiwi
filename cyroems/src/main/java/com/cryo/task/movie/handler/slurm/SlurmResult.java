package com.cryo.task.movie.handler.slurm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlurmResult
{
    private String jobId;
    private String bashFile;
    private String logFile;
    private String node;
    private String state;
    private String exitcode;
}
