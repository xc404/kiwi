package com.cryo.task;

import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.task.detect.TaskDetector;
import com.cryo.task.movie.MovieEngine;
import com.cryo.task.tilt.MdocEngine;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;

@Slf4j
public class TaskMonitor
{
    @Getter
    private final Task task;
    private final TaskDetector movieDetector;
    private final MovieEngine taskMovieEngine;
    private MdocEngine taskMdocEngine;


    public TaskMonitor(Task task, ApplicationContext applicationContext) {
        this.task = task;
        this.taskMovieEngine = new MovieEngine(task, applicationContext);
        this.movieDetector = new TaskDetector(task, applicationContext);
        TaskDataSetRepository taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        TaskDataset dataset = taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
        if(dataset.getIs_tomo()){
            this.taskMdocEngine = new MdocEngine(task, applicationContext);
        }
    }


    public void start(){
        this.movieDetector.start();
        this.taskMovieEngine.start();
        if(taskMdocEngine != null){
            this.taskMdocEngine.start();
        }
    }

    public void stop(){
        safeStop(this.movieDetector);
        safeStop(this.taskMovieEngine);
        safeStop(this.taskMdocEngine);
    }


    private void safeStop(Lifecycle stoppable) {
        if (stoppable != null) {
            try {
                stoppable.stop();
            } catch (Exception e) {
                // 可根据实际需求记录日志或做其他处理
                log.error(e.getMessage(),e);
            }
        }
    }
}
