package com.cryo.ctl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.UserRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.service.TaskService;
import com.cryo.service.cryosparc.CryosparcClient;
import com.mongodb.client.MongoDatabase;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
public class HealthCheckCtl {
    private final TaskRepository taskRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final TaskDataSetRepository taskDataSetRepository;
    private final UserRepository userRepository;
    private final TaskService taskService;
    private final CryosparcClient cryosparcClient;
    private final MongoTemplate mongoTemplate;

    /**
     * 整体系统健康状态检查 - 整合所有组件状态
     * GET /api/health
     */
    @GetMapping
    @ResponseBody
    public ResponseEntity<HealthStatus> getSystemHealth() {
        HealthStatus status = new HealthStatus();
        status.setTimestamp(LocalDateTime.now());

        try {
            log.info("Starting system health check");

            // 收集所有组件的健康状态
            Map<String, JSONObject> components = new HashMap<>();

            // 数据库健康检查
            components.put("database", checkDatabaseHealth());
            // Cryosparc健康检查
            components.put("cryosparc", checkCryosparcHealth());
            // 任务队列健康检查
            components.put("taskQueue", checkTaskQueueHealth(1));

            status.setComponents(components);

            // 计算整体状态
            boolean allHealthy = components.values().stream()
                    .allMatch(comp -> "OK".equals(comp.getString("status")));

            status.setStatus(allHealthy ? "OK" : "DEGRADED");

            // 如果有组件不健康，收集错误信息
            List<String> errors = components.entrySet().stream()
                    .filter(entry -> !"OK".equals(entry.getValue().getString("status")))
                    .map(entry -> entry.getKey() + ": " + entry.getValue().getString("error"))
                    .collect(Collectors.toList());

            if (!errors.isEmpty()) {
                status.setError(String.join("; ", errors));
            }

        } catch (Exception e) {
            log.error("Health check failed with exception", e);
            status.setStatus("DOWN");
            status.setError(e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(status);
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/database")
    @ResponseBody
    public ResponseEntity<JSONObject> getDatabseHealth() {
        return ResponseEntity.ok(this.checkDatabaseHealth());
    }

    @GetMapping("/tasks_{lastXDays}")
    @ResponseBody
    public ResponseEntity<JSONObject> getTaskHealth(@PathVariable int lastXDays) {
        return ResponseEntity.ok(this.checkTaskQueueHealth(lastXDays));
    }

    @GetMapping("/cryosparc")
    @ResponseBody
    public ResponseEntity<JSONObject> getCryosparcHealth() {
        JSONObject health = new JSONObject();
        try {
            health = this.checkCryosparcHealth();
            String status = health.getString("status");
            HttpStatus httpStatus = "OK".equalsIgnoreCase(status) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(httpStatus).body(health);
        } catch (Exception e) {
            log.error("Cryosparc health check failed", e);
            health.put("status", "DOWN");
            health.put("connected", false);
            health.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            health.put("message", "Cryosparc health check failed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    private JSONObject checkDatabaseHealth() {
        DatabaseHealth health = new DatabaseHealth();
        health.setTimestamp(LocalDateTime.now());

        try {
            String dbName = mongoTemplate.getDb().getName();
            // 必须在 admin 数据库上运行此命令
            MongoDatabase adminDb = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase("admin");
            health.setDbName1(adminDb.getName());
            health.setOpLatencies(this.getDatabaseLatency(adminDb));
            try{
                // 执行 replSetGetStatus 命令
                Document rsStatus = adminDb.runCommand(new Document("replSetGetStatus", 1));
                health.setReplSetStatus(rsStatus.toJson());
            } catch (Exception rsEx) {
                health.setReplSetStatus("UNAVAILABLE");
                log.info("replSetGetStatus unavailable possibly due to not a replica set or insufficient permissions");
            }

            health.setStatus("OK");
            health.setDbName(dbName);

        } catch (Exception e) {
            log.error("Database health check failed", e);
            health.setStatus("DOWN");
            health.setError(e.getMessage());
        }

        return JSON.parseObject(JSON.toJSONString(health));
    }

    private List<OpLatency> getDatabaseLatency(MongoDatabase dbConnection) {
        Document stats = dbConnection.runCommand(new Document("serverStatus", 1));
        Document opLatencies = (Document) stats.get("opLatencies");
        Document reads = (Document) opLatencies.get("reads");
        Document writes = (Document) opLatencies.get("writes");
        log.info(opLatencies.toJson());
        OpLatency readLatency = new OpLatency();
        readLatency.setType("reads");
        readLatency.setLatency(reads.getLong("latency"));
        readLatency.setOps(reads.getLong("ops"));
        readLatency.setAvgLatency(reads.getLong("ops") == 0 ? 0 :  (double) reads.getLong("latency") / reads.getLong("ops") / 1000.0);
        OpLatency writeLatency = new OpLatency();
        writeLatency.setType("writes");
        writeLatency.setLatency(writes.getLong("latency"));
        writeLatency.setOps(writes.getLong("ops"));
        writeLatency.setAvgLatency(writes.getLong("ops") == 0 ? 0 : (double) writes.getLong("latency") / writes.getLong("ops") / 1000.0);
        return List.of(readLatency, writeLatency);
    }

    private JSONObject checkTaskQueueHealth(int days) {
        TaskQueueHealth health = new TaskQueueHealth();
        health.setTimestamp(LocalDateTime.now());

        try {
            // 统计最近24小时内的任务
            List<Task> lastDayTasks = taskRepository.findLast24HoursTasks(new Date(System.currentTimeMillis() - days*24L * 60 * 60 * 1000));
            log.info("Task queue health check - Total tasks in last 24 hours: {}", lastDayTasks.size());
            Map<TaskStatus, Long> taskStatusCounts = lastDayTasks.stream()
                    .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));

//            Map<String, Long> exportTaskStatusCounts = exportTaskRepository.findAll().stream()
//                    .collect(Collectors.groupingBy(task -> task.getStatus() != null ? task.getStatus().toString() : "UNKNOWN",
//                            Collectors.counting()));

            // 将枚举键转换为字符串，避免序列化问题
            JSONObject taskStatusCountsJson = new JSONObject();
            taskStatusCounts.forEach((status, count) ->
                taskStatusCountsJson.put(status.toString(), count));

            health.setTaskStatusCounts(taskStatusCounts);
//            health.setExportTaskStatusCounts(exportTaskStatusCounts);
            health.setRunningTaskCount(lastDayTasks.size());

            // 获取任务统计数据
            int totalTasks = lastDayTasks.size();
            long completedTasks = taskStatusCounts.getOrDefault(TaskStatus.finished, 0L);
            long runningTasksCount = taskStatusCounts.getOrDefault(TaskStatus.running, 0L);

            health.setTotalTasks(totalTasks);
            health.setCompletedTasks((int) completedTasks);

            // 根据运行任务数量设置队列状态
            if (runningTasksCount > 10) {
                health.setQueueStatus("OVERLOADED");
            } else if (runningTasksCount > 5) {
                health.setQueueStatus("BUSY");
            } else if (runningTasksCount > 2) {
                health.setQueueStatus("NORMAL");
            } else {
                health.setQueueStatus("IDLE");
            }

            // 计算任务完成率
            double completeRate = !lastDayTasks.isEmpty() ? (double) completedTasks / lastDayTasks.size() : 1;
            if (completeRate > 0.9) {
                health.setHealthLevel("GREEN");
            } else if (completeRate > 0.2) {
                health.setHealthLevel("WARNING");
            } else {
                health.setHealthLevel("ATTENTION");
            }

            health.setStatus("OK");
            log.debug("Task queue health check passed - Queue: {}, Health: {}",
                health.getQueueStatus(), health.getHealthLevel());

        } catch (Exception e) {
            log.error("Task queue health check failed", e);
            health.setStatus("DOWN");
            health.setError(e.getMessage());
        }

        return JSON.parseObject(JSON.toJSONString(health));
    }

    private JSONObject checkCryosparcHealth() {
        return cryosparcClient.testBaseUrlConnection();
    }

    @Data
    public static class HealthStatus {
        private LocalDateTime timestamp;
        private String status;
        private String error;
        private Map<String, JSONObject> components = new HashMap<>();
    }

    @Data
    public static class DatabaseHealth {
        private LocalDateTime timestamp;
        private String status;
        private String error;
        private String dbName;
        private String dbName1;
        private String replSetStatus;
        private List<OpLatency> opLatencies;
    }

    @Data
    public static class TaskQueueHealth {
        private LocalDateTime timestamp;
        private String status;
        private String error;
        private String warning_msg;
        private Map<TaskStatus, Long> taskStatusCounts = new HashMap<>();
        private Map<String, Long> exportTaskStatusCounts = new HashMap<>();
        private double healthScore;
        private String queueStatus;
        private String healthLevel;
        private int runningTaskCount;
        private int totalTasks;
        private int completedTasks;
    }

    @Data
    public static class OpLatency {
        private String type;
        private double latency;
        private double ops;
        private double avgLatency;
    }
}
