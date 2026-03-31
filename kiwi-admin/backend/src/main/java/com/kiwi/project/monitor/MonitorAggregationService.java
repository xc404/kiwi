package com.kiwi.project.monitor;

import com.kiwi.project.monitor.dto.MonitorMetricDto;
import com.kiwi.project.monitor.dto.MonitorModuleDto;
import com.kiwi.project.monitor.dto.MonitorSnapshotDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MonitorAggregationService {

    private final List<MonitorContributor> contributors;

    public MonitorSnapshotDto snapshot() {
        List<MonitorModuleDto> modules = contributors.stream()
            .sorted(Comparator.comparingInt(MonitorContributor::order))
            .map(this::safeModule)
            .collect(Collectors.toList());
        return MonitorSnapshotDto.builder()
            .collectedAt(Instant.now())
            .modules(modules)
            .build();
    }

    private MonitorModuleDto safeModule(MonitorContributor c) {
        try {
            return MonitorModuleDto.builder()
                .id(c.moduleId())
                .title(c.title())
                .order(c.order())
                .metrics(c.collect())
                .build();
        } catch (Exception e) {
            return MonitorModuleDto.builder()
                .id(c.moduleId())
                .title(c.title())
                .order(c.order())
                .metrics(List.of(MonitorMetricDto.builder()
                    .id("collect-error")
                    .label("采集失败")
                    .kind("text")
                    .valueText(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                    .build()))
                .build();
        }
    }
}
