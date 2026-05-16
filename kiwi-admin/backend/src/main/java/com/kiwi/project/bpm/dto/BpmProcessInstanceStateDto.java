package com.kiwi.project.bpm.dto;

import lombok.Data;

import java.util.Date;

/**
 * 机机查询用的轻量流程实例状态（单实例 {@code GET …/state} 与 {@code POST …/states} 批量接口共用）。
 * <p>当 {@link #found} 为 false 时表示历史表中尚无该实例 id（仅填充 {@link #id}）。
 */
@Data
public class BpmProcessInstanceStateDto {

    private String id;
    /** 是否在引擎历史中存在 */
    private boolean found = true;
    /** {@link ProcessInstanceState} 名称，如 RUNNING、ERROR */
    private String state;
    private Boolean ended;
    private Boolean suspended;
    private Date endTime;
    private String deleteReason;
    /**
     * 当 {@code state == ERROR} 时，来自未关闭 incident 的摘要（多 incident 时用分号拼接 message）。
     */
    private String errorReason;
    /** 首个 incident 关联的 BPMN 节点 id */
    private String errorActivityId;
    /** 首个 incident 关联的 BPMN 节点展示名（可能为空） */
    private String errorActivityName;
}
