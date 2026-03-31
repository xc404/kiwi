package com.kiwi.project.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorModuleDto {

    private String id;
    private String title;
    private int order;
    private List<MonitorMetricDto> metrics;
}
