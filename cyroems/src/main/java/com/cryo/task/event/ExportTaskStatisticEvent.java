package com.cryo.task.event;

import com.cryo.task.export.ExportTaskVo;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

public class ExportTaskStatisticEvent extends ApplicationEvent
{
    private final ExportTaskVo exportTaskVo;

    public ExportTaskStatisticEvent(ExportTaskVo exportTaskVo) {
        super(exportTaskVo);
        this.exportTaskVo = exportTaskVo;
    }

    public ExportTaskVo getExportTaskVo() {
        return exportTaskVo;
    }
}
