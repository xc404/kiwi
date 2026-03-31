package com.kiwi.project.monitor.contributor;

import com.kiwi.project.monitor.MonitorContributor;
import com.kiwi.project.monitor.dto.MonitorMetricDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 主数据源（JDBC）连通性与元数据；使用 {@link ObjectProvider} 以便在无数据源场景下跳过。
 */
@Component
@RequiredArgsConstructor
public class JdbcDataSourceMonitorContributor implements MonitorContributor {

    private static final Pattern JDBC_PASSWORD = Pattern.compile("([?&;]password=)[^&;]*", Pattern.CASE_INSENSITIVE);

    private final ObjectProvider<DataSource> dataSourceProvider;

    @Override
    public String moduleId() {
        return "jdbc";
    }

    @Override
    public String title() {
        return "数据库 (JDBC)";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<MonitorMetricDto> collect() {
        DataSource ds = dataSourceProvider.getIfAvailable();
        if (ds == null) {
            return List.of(MonitorMetricDto.builder()
                .id("no-datasource")
                .label("状态")
                .kind("text")
                .valueText("未配置 DataSource")
                .build());
        }
        List<MonitorMetricDto> m = new ArrayList<>();
        long t0 = System.nanoTime();
        try (Connection conn = ds.getConnection()) {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            m.add(MonitorMetricDto.builder()
                .id("jdbc-latency")
                .label("连接耗时")
                .kind("number")
                .value((double) ms)
                .unit("ms")
                .build());
            m.add(MonitorMetricDto.builder()
                .id("jdbc-ok")
                .label("连通性")
                .kind("boolean")
                .value(1.0)
                .valueText("正常")
                .build());

            DatabaseMetaData meta = conn.getMetaData();
            m.add(MonitorMetricDto.builder()
                .id("db-product")
                .label("数据库产品")
                .kind("text")
                .valueText(meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion())
                .build());
            m.add(MonitorMetricDto.builder()
                .id("jdbc-driver")
                .label("JDBC 驱动")
                .kind("text")
                .valueText(meta.getDriverName() + " " + meta.getDriverVersion())
                .build());
            String url = meta.getURL();
            m.add(MonitorMetricDto.builder()
                .id("jdbc-url")
                .label("JDBC URL（脱敏）")
                .kind("text")
                .valueText(maskJdbcUrl(url))
                .build());
        } catch (Exception e) {
            m.add(MonitorMetricDto.builder()
                .id("jdbc-ok")
                .label("连通性")
                .kind("boolean")
                .value(0.0)
                .valueText("失败: " + e.getMessage())
                .build());
        }
        return m;
    }

    static String maskJdbcUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return JDBC_PASSWORD.matcher(url).replaceAll("$1***");
    }
}
