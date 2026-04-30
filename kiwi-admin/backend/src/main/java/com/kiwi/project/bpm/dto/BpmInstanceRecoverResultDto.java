package com.kiwi.project.bpm.dto;

import lombok.Data;

/**
 * 按流程实例一键恢复（为失败 Job / External Task 重置重试次数）的结果摘要。
 */
@Data
public class BpmInstanceRecoverResultDto {

    /** 调用时该实例上处于 OPEN 的 incident 数量 */
    private int openIncidentCount;
    /** 已对其执行 {@code setJobRetries} 的去重后 Job 数量 */
    private int jobsRetried;
    /** 已对其执行 external task {@code setRetries} 的去重后 External Task 数量 */
    private int externalTasksRetried;
    /** 本次未处理（类型不支持或 configuration 为空）的 incident 数量 */
    private int incidentsSkipped;
    /** 实际写入的重试次数 */
    private int retriesApplied;
}
