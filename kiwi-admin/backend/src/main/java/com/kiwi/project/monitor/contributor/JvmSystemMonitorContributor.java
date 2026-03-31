package com.kiwi.project.monitor.contributor;

import com.kiwi.project.monitor.MonitorContributor;
import com.kiwi.project.monitor.dto.MonitorMetricDto;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * JVM 与进程级 CPU、内存、线程等。
 */
@Component
public class JvmSystemMonitorContributor implements MonitorContributor {

    @Override
    public String moduleId() {
        return "jvm-system";
    }

    @Override
    public String title() {
        return "JVM / 系统";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<MonitorMetricDto> collect() {
        List<MonitorMetricDto> m = new ArrayList<>();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sun) {
            double procCpu = sun.getProcessCpuLoad();
            if (procCpu >= 0) {
                m.add(MonitorMetricDto.builder()
                    .id("process-cpu")
                    .label("进程 CPU")
                    .kind("percent")
                    .value(procCpu * 100.0)
                    .unit("%")
                    .build());
            }
            double sysCpu = sun.getSystemCpuLoad();
            if (sysCpu >= 0) {
                m.add(MonitorMetricDto.builder()
                    .id("system-cpu")
                    .label("系统 CPU")
                    .kind("percent")
                    .value(sysCpu * 100.0)
                    .unit("%")
                    .build());
            }
            long free = sun.getFreePhysicalMemorySize();
            long total = sun.getTotalPhysicalMemorySize();
            if (total > 0) {
                double usedPct = 100.0 * (total - free) / total;
                m.add(MonitorMetricDto.builder()
                    .id("host-memory-used")
                    .label("物理内存占用")
                    .kind("percent")
                    .value(usedPct)
                    .unit("%")
                    .build());
                m.add(MonitorMetricDto.builder()
                    .id("host-memory-free")
                    .label("物理内存空闲")
                    .kind("bytes")
                    .value((double) free)
                    .unit("B")
                    .build());
            }
        }

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        if (heap.getMax() > 0) {
            double heapPct = 100.0 * heap.getUsed() / heap.getMax();
            m.add(MonitorMetricDto.builder()
                .id("heap-used-ratio")
                .label("堆内存占用")
                .kind("percent")
                .value(heapPct)
                .unit("%")
                .build());
        }
        m.add(MonitorMetricDto.builder()
            .id("heap-used")
            .label("堆已用")
            .kind("bytes")
            .value((double) heap.getUsed())
            .unit("B")
            .build());
        m.add(MonitorMetricDto.builder()
            .id("heap-max")
            .label("堆上限")
            .kind("bytes")
            .value(heap.getMax() >= 0 ? (double) heap.getMax() : null)
            .unit("B")
            .build());

        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        m.add(MonitorMetricDto.builder()
            .id("thread-count")
            .label("活动线程数")
            .kind("number")
            .value((double) threads.getThreadCount())
            .build());

        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        m.add(MonitorMetricDto.builder()
            .id("uptime")
            .label("进程运行时间")
            .kind("number")
            .value((double) rt.getUptime())
            .unit("ms")
            .build());

        return m;
    }
}
