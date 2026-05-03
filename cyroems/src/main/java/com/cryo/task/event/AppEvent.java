package com.cryo.task.event;

import org.springframework.context.ApplicationEvent;

import java.time.Clock;

public class AppEvent extends ApplicationEvent
{
    public AppEvent(Object source) {
        super(source);
    }

    public AppEvent(Object source, Clock clock) {
        super(source, clock);
    }
}
