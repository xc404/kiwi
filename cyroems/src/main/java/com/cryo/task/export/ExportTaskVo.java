package com.cryo.task.export;

import com.cryo.model.Task;
import com.cryo.model.export.ExportTask;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExportTaskVo
{
    private Task task;
    private ExportTask exportTask;
}
