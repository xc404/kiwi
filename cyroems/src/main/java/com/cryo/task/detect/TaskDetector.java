package com.cryo.task.detect;

import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class TaskDetector implements  Lifecycle
{
    private final Task task;
    private final TaskScheduler taskScheduler;
    private final TaskRepository taskRepository;
    private final MovieDetectorSupport movieDetectorSupport;
    private final MDocDetectorSupport mDocDetectorSupport;
    private final TaskDataset dataset;
    private ScheduledFuture<?> scheduledFuture;

    public TaskDetector(Task task, ApplicationContext applicationContext) {
        this.task = task;
        this.taskScheduler = applicationContext.getBean(TaskScheduler.class);
        this.taskRepository = applicationContext.getBean(TaskRepository.class);;
        this.movieDetectorSupport = applicationContext.getBean(MovieDetectorSupport.class);
        this.mDocDetectorSupport = applicationContext.getBean(MDocDetectorSupport.class);
        TaskDataSetRepository taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.dataset = taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();

    }
    public synchronized void detect(){
        try {
            Date date = new Date();
            this.movieDetectorSupport.detect(task);
            if(this.dataset.getIs_tomo()){
                mDocDetectorSupport.detect(task);
            }
            task.setLast_detect_time( date);
            this.taskRepository.updateLastDetectTime(task.getId(),date);
        }catch( Exception e ){
            log.error(e.getMessage(),e);
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
