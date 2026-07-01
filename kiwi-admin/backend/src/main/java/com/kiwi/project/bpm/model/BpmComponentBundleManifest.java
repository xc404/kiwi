package com.kiwi.project.bpm.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 组件插件 JAR 内 {@code META-INF/kiwi/component-bundle.json} 的包级清单。
 * 模板包 zip 场景可额外填充 {@link #fileName}、{@link #sha256}。
 */
@Data
@Schema(description = "组件插件包清单（component-bundle.json）")
public class BpmComponentBundleManifest {

    public static final String SchemaVersionValue = "1";
    public static final String BundleResourcePath = "META-INF/kiwi/component-bundle.json";

    @Schema(description = "清单 schema 版本，固定为 \"1\"", requiredMode = Schema.RequiredMode.REQUIRED)
    private String schemaVersion;

    @Schema(description = "包显示名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "SemVer 包版本", requiredMode = Schema.RequiredMode.REQUIRED)
    private String version;

    @Schema(description = "一行简介")
    private String summary;

    @Schema(description = "详细说明")
    private String description;

    @Schema(description = "长文说明（Markdown 纯文本）")
    private String readme;

    @Schema(description = "作者")
    private String author;

    @Schema(description = "发布者")
    private String publisher;

    @Schema(description = "许可证（SPDX 或自由文本）")
    private String license;

    @Schema(description = "最低 Kiwi 版本")
    private String kiwiMinVersion;

    @Schema(description = "主页 URL")
    private String homepage;

    @Schema(description = "源码仓库 URL")
    private String repository;

    @Schema(description = "模板包场景：JAR 文件名")
    private String fileName;

    @Schema(description = "模板包场景：JAR SHA-256")
    private String sha256;

    @Schema(description = "组件条目列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<BpmComponentBundleComponentEntry> components;
}
