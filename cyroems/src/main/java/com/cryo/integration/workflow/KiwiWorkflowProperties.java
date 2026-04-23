package com.cryo.integration.workflow;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cryoEMS 调用 Kiwi-admin 启动 Camunda 流程。
 */
@Data
@ConfigurationProperties(prefix = "app.kiwi.workflow")
public class KiwiWorkflowProperties {

    private boolean enabled = false;
    /** Kiwi-admin 根 URL，无尾部斜杠 */
    private String baseUrl = "http://127.0.0.1:8080";
    /** Kiwi `BpmProcess.id`（库中流程主键） */
    private String movieProcessDefinitionId = "";
    /** 与 Kiwi {@code kiwi.integration.machine.secret} 一致 */
    private String integrationSecret = "";
    /** {@link com.cryo.task.movie.MovieEngine} 每轮最多尝试拉起 Kiwi 实例的条数（替代原线程池 idle 上限） */
    private int movieBatchSize = 20;
}
