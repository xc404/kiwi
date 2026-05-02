package com.kiwi.bpmn.component.slurm;

/**
 * Mongo 中 Slurm 作业跟踪记录的业务状态（与 Slurm 调度器作业状态不同，表示本系统跟踪生命周期）。
 */
public enum SlurmJobStatus {

    /** 已提交，正由 {@link SlurmJobTracker} 通过 sacct 轮询直至终态 */
    RUNNING,

    /**
     * 已抢到终态处理权，正在向 Camunda 上报 complete / handleFailure；{@link SlurmJobTracker} 不再将其当作
     * {@link #RUNNING} 轮询，避免与 sacct 或其它入口重复上报。
     */
    REPORTING_TERMINAL,

    /** 已通过 Camunda 上报终态；文档保留在库中，不再参与 sacct 轮询（仅 {@link #RUNNING} 会被加载） */
    TERMINATED
}
