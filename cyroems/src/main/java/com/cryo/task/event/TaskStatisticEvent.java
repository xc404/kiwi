package com.cryo.task.event;

import com.cryo.model.Task;
import com.cryo.task.export.ExportTaskVo;
import org.springframework.context.ApplicationEvent;

public class TaskStatisticEvent extends ApplicationEvent
{
    private final Task task;

    public TaskStatisticEvent(Task exportTaskVo) {
        super(exportTaskVo);
        this.task = exportTaskVo;
    }

    public Task getTask() {
        return task;
    }
}
