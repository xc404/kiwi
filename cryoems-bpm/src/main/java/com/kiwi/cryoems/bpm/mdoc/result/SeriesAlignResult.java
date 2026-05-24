package com.kiwi.cryoems.bpm.mdoc.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 与 cyroems {@code com.cryo.task.tilt.seriesalign.SeriesAlignResult} 字段命名严格一致；
 * 由 {@code tilt_series_align.py} 产物隐式派生（脚本不显式 -ou 但落到约定路径）。
 *
 * <ul>
 *     <li>{@code modelFileOutput} —— {@code ${name}.3dmod}</li>
 *     <li>{@code residualFileOutput} —— {@code ${name}.resid}</li>
 *     <li>{@code fidXYZOutput} —— {@code ${name}fid.xyz}（cyroems 命名特殊：name 与 "fid" 直接相连无下划线）</li>
 *     <li>{@code tiltFileOutput} —— {@code ${name}.tlt}</li>
 *     <li>{@code xAxisTiltOutput} —— {@code ${name}.xtilt}</li>
 *     <li>{@code transformOutput} —— {@code ${name}.tltxf}</li>
 *     <li>{@code filledInModelOutput} —— {@code ${name}_nogaps.fid}</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeriesAlignResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String modelFileOutput;
    private String residualFileOutput;
    private String fidXYZOutput;
    private String tiltFileOutput;
    private String xAxisTiltOutput;
    private String transformOutput;
    private String filledInModelOutput;
}
