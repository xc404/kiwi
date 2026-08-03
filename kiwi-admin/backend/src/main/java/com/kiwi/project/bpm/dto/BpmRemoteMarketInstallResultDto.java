package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "远程市场安装结果")
public class BpmRemoteMarketInstallResultDto {

    private String type;
    private String slug;
    private String version;
    private String projectId;
    private String projectName;
    private String pluginFileName;
    private List<String> installedComponentKeys = new ArrayList<>();
}
