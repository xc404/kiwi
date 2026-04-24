package com.kiwi.project.monitor.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.project.monitor.MonitorAggregationService;
import com.kiwi.project.monitor.dto.MonitorSnapshotDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监控快照：聚合 {@link com.kiwi.project.monitor.MonitorContributor} 实现。
 */
@SaCheckLogin
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
@Tag(name = "监控", description = "聚合监控快照")
public class MonitorCtl {

    private final MonitorAggregationService monitorAggregationService;

    @Operation(operationId = "mon_snapshot", summary = "获取监控快照（各模块聚合指标）")
    @GetMapping("/snapshot")
    public MonitorSnapshotDto snapshot() {
        return monitorAggregationService.snapshot();
    }
}
