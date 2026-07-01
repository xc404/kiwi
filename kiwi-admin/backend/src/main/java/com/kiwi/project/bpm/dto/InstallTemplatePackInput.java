package com.kiwi.project.bpm.dto;

import lombok.Data;

@Data
public class InstallTemplatePackInput {

    private String projectName;
    private String targetProjectId;
}
