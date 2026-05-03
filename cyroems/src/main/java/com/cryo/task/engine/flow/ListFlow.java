package com.cryo.task.engine.flow;

import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.TaskStep;

import java.util.List;

/**
 * @deprecated 流程顺序编排已迁移至 Kiwi Camunda；保留仅为兼容导出/MDoc 等仍在本进程内用 IFlow 的场景。
 */
@Deprecated(since = "1.0.0", forRemoval = false)
public class ListFlow<T extends Instance, R extends InstanceResult> implements IFlow<T,R>
{
    private final List<TaskStep> taskStepList;

    public ListFlow(List<TaskStep> taskStepList) {
        this.taskStepList = taskStepList;
    }

    @Override
    public TaskStep next(Context<T,R> context, TaskStep step) {
        int index = taskStepList.indexOf(step);
        if( index >= 0 ) {
            index = index + 1;
            if( index < taskStepList.size() ) {

                return taskStepList.get(index);
            }
        }
        return null;
    }
}
