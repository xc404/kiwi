package com.cryo.task.export;

import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportTask;
import com.cryo.task.export.detect.ExportTaskDetector;
import com.cryo.task.export.handler.batch.BatchExportHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;

@Slf4j
public class ExportMonitor
{
    @Getter
    private final Task task;
    private final ExportTask exportTask;
    private final ExportMovieEngine taskMovieEngine;
    private final ApplicationContext applicationContext;
    private final TaskDataset dataset;
    private final ExportTaskRepository exportTaskRepository;
    private ExportMdocEngine exportMdocEngine;
    private final ExportTaskDetector exportTaskDetector;

    public ExportMonitor(ExportTaskVo exportTaskVo, ApplicationContext applicationContext) {
        this.task = exportTaskVo.getTask();
        this.applicationContext = applicationContext;
        this.exportTask = exportTaskVo.getExportTask();
        this.taskMovieEngine = new ExportMovieEngine(task, exportTask, applicationContext);
        this.exportTaskRepository = applicationContext.getBean(ExportTaskRepository.class);
        TaskDataSetRepository taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.dataset = taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
        if( dataset.getIs_tomo() && !exportTask.isCryosparc() ) {
            this.exportMdocEngine = new ExportMdocEngine(task, exportTask, applicationContext);
        }
        exportTaskDetector = new ExportTaskDetector(task, exportTask, applicationContext);

    }


    public void start() {
        if( task.isCleaned() ) {
            log.warn("task has been cleaned " + task.getId());
            this.exportTask.setStatus(TaskStatus.finished);
            this.exportTask.setErrorSummary("Task has been cleaned");
            this.exportTaskRepository.save(exportTask);
            return;
        }


        if( (dataset.getMovie_sync_done() || task.getStatus() == TaskStatus.finished) && !exportTask.isCryosparc() ) {
            BatchExportHandler batchExportHandler = new BatchExportHandler(applicationContext, task, exportTask);
            batchExportHandler.export();
        } else {
            this.exportTaskDetector.start();
            this.taskMovieEngine.start();
            if( exportMdocEngine != null ) {
                this.exportMdocEngine.start();
            }
        }

    }

    public void stop() {
        safeStop(this.taskMovieEngine);
        safeStop(this.exportMdocEngine);
        safeStop(this.exportTaskDetector);
    }


    private void safeStop(Lifecycle stoppable) {
        if( stoppable != null ) {
            try {
                stoppable.stop();
            } catch( Exception e ) {
                // 可根据实际需求记录日志或做其他处理
                log.error(e.getMessage(), e);
            }
        }
    }
}
