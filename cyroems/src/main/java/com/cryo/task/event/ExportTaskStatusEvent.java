package com.cryo.task.event;

import com.cryo.model.TaskStatus;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

public class ExportTaskStatusEvent extends AppEvent
{
    private final String id;
    private final TaskStatus status;

    public ExportTaskStatusEvent(String id, TaskStatus status) {
        super(id);
        this.id = id;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public TaskStatus getStatus() {
        return status;
    }
}
