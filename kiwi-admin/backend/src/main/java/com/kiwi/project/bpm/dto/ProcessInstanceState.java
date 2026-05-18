package com.kiwi.project.bpm.dto;

/**
 * 流程实例生命周期 / 查询状态（{@link BpmProcessInstanceDto#getState()}）。
 */
public enum ProcessInstanceState
{

    /** 运行中 */
    RUNNING,
    /** 已挂起 */
    SUSPENDED,
    /** 正常结束 */
    COMPLETED,
    /** 取消结束 */
    CANCELED,
    /** 历史未结束且无运行实例时的兜底 */
    ACTIVE,
    /** 流程引擎错误或状态无法归类 */
    ERROR
}
