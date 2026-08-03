package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "远程市场索引条目")
public class BpmRemoteMarketItemDto {

    @Schema(description = "来源 ID")
    private String sourceId;

    @Schema(description = "来源名称")
    private String sourceName;

    @Schema(description = "类型：template 或 plugin")
    private String type;

    private String slug;
    private String name;
    private String version;
    private String summary;
    private String category;
    private List<String> tags = new ArrayList<>();
    private String kiwiMinVersion;
    private String downloadUrl;
    private String sha256;
    private String manifestUrl;
    private String signatureUrl;
    private String kind;
    private Integer processCount;
    private List<String> requiredComponentKeys = new ArrayList<>();
    private List<String> componentKeys = new ArrayList<>();
    private MavenCoordinate mavenCoordinate;

    @Schema(description = "与当前 Kiwi 版本是否兼容")
    private boolean kiwiCompatible;

    @Schema(description = "缺失的 requiredComponentKeys（仅模板）")
    private List<String> missingComponentKeys = new ArrayList<>();

    @Data
    @Schema(description = "Maven 坐标")
    public static class MavenCoordinate {
        private String groupId;
        private String artifactId;
        private String version;
    }
}
