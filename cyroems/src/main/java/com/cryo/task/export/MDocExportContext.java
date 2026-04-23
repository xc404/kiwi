package com.cryo.task.export;

import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExporMDocResult;
import com.cryo.model.export.ExportMDocInstance;
import com.cryo.model.export.ExportTask;
import com.cryo.task.engine.BaseContext;
import com.cryo.task.engine.flow.IFlow;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

public class MDocExportContext extends BaseContext<ExportMDocInstance, ExporMDocResult>
{

    @Getter
    private final ExportTask exportTask;
    @Getter
    private final MDoc mDoc;

    public MDocExportContext(ApplicationContext applicationContext,
                             TaskDataset taskDataset, IFlow<ExportMDocInstance, ExporMDocResult> flow,
                             Task task,
                             ExportTask exportTask, ExportMDocInstance exportMDocInstance, MDoc mDoc) {
        super(applicationContext, taskDataset, flow, task, exportMDocInstance, ExporMDocResult.class);
        this.exportTask = exportTask;
        this.mDoc = mDoc;
    }

    public String getCurrentConfigId() {
        return this.exportTask.getId();
    }

}
