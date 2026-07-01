package com.kiwi.project.bpm.dto;

import lombok.Data;

@Data
public class InstallTemplatePackResult {

    private String projectId;
    private String packId;
    private int processCount;
}
