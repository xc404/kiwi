package com.kiwi.project.bpm.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * {@link BpmComponentBundleManifest#getComponents()} 中的单条组件信息（市场展示子集，不含完整 inputs/outputs）。
 */
@Data
@Schema(description = "组件包清单中的组件条目")
public class BpmComponentBundleComponentEntry {

    @Schema(description = "@Component bean key，运行时 id 为 plugin_{key}", requiredMode = Schema.RequiredMode.REQUIRED)
    private String key;

    @Schema(description = "显示名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "设计器分组")
    private String group;

    @Schema(description = "组件版本")
    private String version;

    @Schema(description = "组件说明")
    private String description;

    @Schema(description = "SpringBean 或 SpringExternalTask")
    private String type;

    @Schema(description = "继承父组件 key（非 id）")
    private String parentKey;

    @Schema(description = "依赖的父组件 key 列表")
    private List<String> requiredParentKeys;
}
