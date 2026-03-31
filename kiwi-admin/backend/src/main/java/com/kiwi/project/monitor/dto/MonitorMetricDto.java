package com.kiwi.project.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条监控指标；{@code kind} 决定前端渲染方式，便于扩展新类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorMetricDto {

    private String id;
    private String label;
    /**
     * percent | number | text | bytes | boolean
     */
    private String kind;
    /** percent/number/boolean 等数值含义 */
    private Double value;
    /** 文本类展示 */
    private String valueText;
    private String unit;
}
