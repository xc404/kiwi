package com.kiwi.project.bpm.dto;

import com.kiwi.project.bpm.model.BpmComponentBundleManifest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 单个插件 JAR 的只读描述（列表、预览、上传校验）。
 */
@Data
@Schema(description = "BPM 组件插件 JAR 描述")
public class BpmComponentPluginDescriptor {

    @Schema(description = "JAR 文件名")
    private String fileName;

    @Schema(description = "包级清单（无 JSON 时由文件名与注解扫描回退填充）")
    private BpmComponentBundleManifest bundle;

    @Schema(description = "组件列表（含 manifest 与 scanned 合并结果）")
    private List<BpmComponentPluginComponentInfo> components;

    @Schema(description = "非阻断校验警告")
    private List<String> warnings;

    @Schema(description = "文件大小（字节）")
    private long fileSizeBytes;

    @Schema(description = "文件 SHA-256 十六进制")
    private String sha256;
}
