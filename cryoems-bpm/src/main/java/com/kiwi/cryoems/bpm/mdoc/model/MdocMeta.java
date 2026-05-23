package com.kiwi.cryoems.bpm.mdoc.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * mdoc 元数据聚合根：与 cyroems {@code com.cryo.task.tilt.MDocMeta} 对齐。
 *
 * <p>由 {@link com.kiwi.cryoems.bpm.mdoc.support.MdocFileParser} 从 {@code *.mrc.mdoc} 文本文件
 * 解析生成，承载顶部 header 字段（{@code DataMode} / {@code ImageSize} / {@code PixelSpacing} /
 * {@code Voltage} 等）与每个 {@code [ZValue = n]} 段对应的 tilt 列表。</p>
 */
@Data
public class MdocMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer dataMode;
    private double[] imageSize;
    private String imageFile;
    private Double pixelSpacing;
    private String tomography;
    private Double tiltAxisAngle;
    private Integer binning;
    private Integer spotSize;
    private Double voltage;
    private List<MdocRawTiltMeta> rawTiltMetas;
    private List<MdocTiltMeta> tilts;
}
