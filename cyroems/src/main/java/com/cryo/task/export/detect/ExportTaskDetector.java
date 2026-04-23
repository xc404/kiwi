package com.cryo.task.export.detect;

import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportTask;
import com.cryo.task.detect.MDocDetectorSupport;
import com.cryo.task.detect.MovieDetectorSupport;
import com.cryo.task.export.ExportTaskVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class ExportTaskDetector implements Lifecycle
{
    private final Task task;
    private final TaskScheduler taskScheduler;
    private final TaskRepository taskRepository;
    private final MovieDetectorSupport movieDetectorSupport;
    private final MDocDetectorSupport mDocDetectorSupport;
    private final TaskDataset dataset;
    private ScheduledFuture<?> scheduledFuture;
    private final ExportTask exportTask;

    public ExportTaskDetector(Task task, ExportTask exportTask, ApplicationContext applicationContext) {
        this.task = task;
        this.taskScheduler = applicationContext.getBean(TaskScheduler.class);
        this.taskRepository = applicationContext.getBean(TaskRepository.class);
        this.movieDetectorSupport = applicationContext.getBean(MovieDetectorSupport.class);
        this.mDocDetectorSupport = applicationContext.getBean(MDocDetectorSupport.class);
        TaskDataSetRepository taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.dataset = taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
        this.exportTask = exportTask;

    }

    public synchronized void detect() {
        try {
            Date date = new Date();
            ExportTaskVo exportTaskVo = new ExportTaskVo(task, exportTask);
            this.movieDetectorSupport.detectExport(exportTaskVo);
            if( this.dataset.getIs_tomo() && !exportTask.isCryosparc() ) {
                mDocDetectorSupport.detectExport(exportTaskVo);
            }
            exportTask.setLast_detect_time(date);
            this.taskRepository.updateLastDetectTime(task.getId(), date);
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void start() {
        this.scheduledFuture = this.taskScheduler.scheduleWithFixedDelay(() -> {
            detect();
        }, Duration.ofSeconds(10));
    }


    @Override
    public void stop() {
        this.scheduledFuture.cancel(true);
    }

    @Override
    public boolean isRunning() {
        return !this.scheduledFuture.isCancelled();
    }
}
