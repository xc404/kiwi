package com.cryo.task.event;

import com.cryo.model.TaskStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class TaskStatusEvent extends AppEvent {
    private final String id;
    private final TaskStatus status;

    public TaskStatusEvent(String id, TaskStatus status) {
        super(id);
        this.id = id;
        this.status = status;
    }


}
