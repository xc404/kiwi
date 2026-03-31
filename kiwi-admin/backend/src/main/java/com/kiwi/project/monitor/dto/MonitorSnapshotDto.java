package com.kiwi.project.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorSnapshotDto {

    private Instant collectedAt;
    private List<MonitorModuleDto> modules;
}
