package com.cryo.task.filedetect;

import com.cryo.model.Task;

import java.io.File;

public interface TaskFileHandler {
    public boolean support(String suffix);
    void handle(Task task,File file);
}
