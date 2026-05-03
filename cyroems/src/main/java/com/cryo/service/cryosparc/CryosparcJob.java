package com.cryo.service.cryosparc;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CryosparcJob
{
    private JobRequest jobRequest;
    private JobResult jobResult;
    private JobState jobState;
}
