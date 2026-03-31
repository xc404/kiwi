package com.kiwi.project.monitor.contributor;

import com.kiwi.project.monitor.MonitorContributor;
import com.kiwi.project.monitor.dto.MonitorMetricDto;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB 连通性（ping）；无工厂 Bean 时跳过。
 */
@Component
@RequiredArgsConstructor
public class MongoDbMonitorContributor implements MonitorContributor {

    private final ObjectProvider<MongoDatabaseFactory> mongoDatabaseFactory;

    @Override
    public String moduleId() {
        return "mongodb";
    }

    @Override
    public String title() {
        return "MongoDB";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public List<MonitorMetricDto> collect() {
        MongoDatabaseFactory factory = mongoDatabaseFactory.getIfAvailable();
        if (factory == null) {
            return List.of(MonitorMetricDto.builder()
                .id("mongo-unavailable")
                .label("状态")
                .kind("text")
                .valueText("未启用 MongoDB")
                .build());
        }
        List<MonitorMetricDto> m = new ArrayList<>();
        long t0 = System.nanoTime();
        try {
            Document pong = factory.getMongoDatabase().runCommand(new Document("ping", 1));
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            double ok = 0.0;
            Object okVal = pong.get("ok");
            if (okVal instanceof Number n && n.doubleValue() > 0) {
                ok = 1.0;
            } else if (Boolean.TRUE.equals(okVal)) {
                ok = 1.0;
            }
            m.add(MonitorMetricDto.builder()
                .id("mongo-ping-ms")
                .label("ping 耗时")
                .kind("number")
                .value((double) ms)
                .unit("ms")
                .build());
            m.add(MonitorMetricDto.builder()
                .id("mongo-ok")
                .label("连通性")
                .kind("boolean")
                .value(ok)
                .valueText(ok > 0 ? "正常" : "异常")
                .build());
            m.add(MonitorMetricDto.builder()
                .id("mongo-db")
                .label("数据库名")
                .kind("text")
                .valueText(factory.getMongoDatabase().getName())
                .build());
        } catch (Exception e) {
            m.add(MonitorMetricDto.builder()
                .id("mongo-ok")
                .label("连通性")
                .kind("boolean")
                .value(0.0)
                .valueText("失败: " + e.getMessage())
                .build());
        }
        return m;
    }
}
