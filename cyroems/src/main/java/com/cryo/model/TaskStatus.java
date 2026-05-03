package com.cryo.model;

public enum TaskStatus {
    running,
    stopped,
    archived,
    finished;

    public boolean isFinished() {

        return this == finished || this == archived;
    }
}
