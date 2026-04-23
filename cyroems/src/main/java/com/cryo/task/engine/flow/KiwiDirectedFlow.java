package com.cryo.task.engine.flow;

import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.TaskStep;

/**
 * 占位 {@link IFlow}：步骤顺序完全由 Kiwi Camunda 编排，本进程内禁止再调用 {@link #next} 推导下一步。
 */
public final class KiwiDirectedFlow {

    private static final IFlow<?, ?> INSTANCE = (context, step) -> {
        throw new UnsupportedOperationException(
                "IFlow#next 已废弃：步骤顺序由 Kiwi-admin Camunda BPMN 编排。");
    };

    private KiwiDirectedFlow() {
    }

    @SuppressWarnings("unchecked")
    public static <T extends Instance, R extends InstanceResult> IFlow<T, R> get() {
        return (IFlow<T, R>) INSTANCE;
    }
}
