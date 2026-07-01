package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 插件 JAR 描述中的单条组件信息（合并清单与注解扫描结果）。
 */
@Data
@Schema(description = "已安装/预览插件中的组件信息")
public class BpmComponentPluginComponentInfo {

    @Schema(description = "组件 bean key")
    private String key;

    @Schema(description = "显示名")
    private String name;

    @Schema(description = "设计器分组")
    private String group;

    @Schema(description = "运行时组件 id（plugin_{key}）")
    private String componentId;

    @Schema(description = "组件说明")
    private String description;

    @Schema(description = "组件版本")
    private String version;

    @Schema(description = "来源：manifest 或 scanned")
    private String source;
}
