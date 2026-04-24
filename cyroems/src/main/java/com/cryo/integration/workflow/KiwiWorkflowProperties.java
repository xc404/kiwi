package com.cryo.integration.workflow;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cryoEMS 与 Kiwi-admin 的流程集成（HTTP 客户端 + Movie 场景扩展字段）。
 */
@Data
@ConfigurationProperties(prefix = "app.kiwi.workflow")
public class KiwiWorkflowProperties {

    private boolean enabled = false;
    /** Kiwi-admin 根 URL，无尾部斜杠 */
    private String baseUrl = "http://127.0.0.1:8080";
    /**
     * 迁移期回退：未在 {@link com.cryo.model.Task#setMovieProcessDefinitionId Task} 上配置时使用。
     * 正式用法为 Task 级 movie-process-definition-id。
     */
    private String movieProcessDefinitionId = "";
    /** 与 Kiwi {@code kiwi.integration.machine.secret} 一致 */
    private String integrationSecret = "";
    /** {@link com.cryo.task.movie.MovieEngine} 每轮最多尝试拉起 Kiwi 实例的条数（替代原线程池 idle 上限） */
    private int movieBatchSize = 20;
    /** {@link KiwiWorkflowClient} / {@link KiwiWorkflowInstanceWatcher} 共用 */
    private ClientProperties client = new ClientProperties();

    @Data
    public static class ClientProperties {
        /** HTTP 连接超时 */
        private int httpConnectTimeoutSeconds = 30;
        /** 单次请求读写超时（启动流程、查询状态等） */
        private int httpRequestTimeoutSeconds = 120;
        /** POST start 返回 429 时的重试间隔（毫秒） */
        private long rateLimitRetryIntervalMillis = 1_000L;
        /** 含首次请求在内的最大尝试次数 */
        private int maxStartAttempts = 5;
        /** 两次成功启动流程之间的最小间隔（毫秒），0 表示不限制（仅客户端软节流） */
        private long minIntervalMillisBetweenStarts = 0L;
        /** {@link KiwiWorkflowInstanceWatcher} 默认轮询间隔（毫秒） */
        private long defaultPollIntervalMillis = 5000L;
    }
}
