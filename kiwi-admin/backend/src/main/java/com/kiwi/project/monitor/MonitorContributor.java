package com.kiwi.project.monitor;

import com.kiwi.project.monitor.dto.MonitorMetricDto;

import java.util.List;

/**
 * 监控模块贡献点：新增实现类并注册为 Spring Bean 即可扩展后端监控项。
 */
public interface MonitorContributor {

    String moduleId();

    String title();

    default int order() {
        return 100;
    }

    List<MonitorMetricDto> collect();
}
