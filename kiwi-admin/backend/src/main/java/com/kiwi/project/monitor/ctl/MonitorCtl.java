package com.kiwi.project.monitor.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.ai.tool.annotation.Tool;
import com.kiwi.project.monitor.MonitorAggregationService;
import com.kiwi.project.monitor.dto.MonitorSnapshotDto;
import io.swagger.v3.oas.annotations.Operation;
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
public class MonitorCtl {

    private final MonitorAggregationService monitorAggregationService;

    @Tool(name = "mon_snapshot", description = "获取监控快照（各模块聚合指标）。")
    @GetMapping("/snapshot")
    @Operation(summary = "获取可扩展监控快照（模块 + 指标）")
    public MonitorSnapshotDto snapshot() {
        return monitorAggregationService.snapshot();
    }
}
