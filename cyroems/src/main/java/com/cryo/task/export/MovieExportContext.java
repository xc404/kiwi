package com.cryo.task.export;

import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportMovieResult;
import com.cryo.model.export.ExportTask;
import com.cryo.task.engine.BaseContext;
import com.cryo.task.engine.flow.IFlow;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

public class MovieExportContext extends BaseContext<ExportMovie, ExportMovieResult>
{

    @Getter
    private final ExportTask exportTask;

    public MovieExportContext(ApplicationContext applicationContext,
                              TaskDataset taskDataset, IFlow<ExportMovie, ExportMovieResult> flow,
                              Task task,
                              ExportTask exportTask, ExportMovie movie) {
        super(applicationContext, taskDataset, flow, task, movie, ExportMovieResult.class);
        this.exportTask = exportTask;
    }

//    @Override
//    public synchronized @NonNull ExportMovieResult getResult() {
//        return super.getResult();
//    }

    public String getContextDir() {
//        if( equalsDefault() ) {
//            return "default";
//        } else {
        return task.getWork_dir() + "/" + exportTask.getTaskWorkDir();
//        }
    }

    @Override
    public String getCurrentConfigId() {
        return this.exportTask.getId();
    }
}
