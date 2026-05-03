package com.cryo.task.movie;

import com.cryo.dao.MDocInstanceRepository;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportMDocInstanceRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportTask;
import com.cryo.service.TaskService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.event.AppEvent;
import com.cryo.task.event.ExportTaskStatisticEvent;
import com.cryo.task.event.ExportTaskStatusEvent;
import com.cryo.task.event.TaskStatisticEvent;
import com.cryo.task.event.TaskStatusEvent;
import com.cryo.task.export.ExportTaskVo;
import com.cryo.task.export.cryosparc.CryosparcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatistic implements ApplicationListener<AppEvent>, ApplicationContextAware
{

    public static Task.Statistic empty = new Task.Statistic(0L, 0L, 0L, 0L, 0L, new Date());
    private final TaskService taskService;
    private final MovieRepository movieRepository;
    private final MovieDataSetRepository movieDataSetRepository;
    private final MDocRepository mDocRepository;
    private final TaskRepository taskRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final MDocInstanceRepository mDocInstanceRepository;
    private final ExportMovieRepository exportMovieRepository;
    private final ExportMDocInstanceRepository exportMDocInstanceRepository;
    private final TaskDataSetRepository taskDataSetRepository;
    private ApplicationContext applicationContext;
    private final CryosparcService cryosparcService;

    @Scheduled(cron = "0 0/10 * * * ?")
    public void execute() {
        List<Task> runningTasks = this.taskService.getRunningTasks();
        runningTasks.forEach(t -> {
            try {

                statisticTask(t);
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        });

    }

    @Scheduled(cron = "0 0/2 * * * ?")
    public void statisticExportTasks() {

        List<ExportTaskVo> runningExportTasks = this.taskService.getRunningExportTasks();
        runningExportTasks.forEach(t -> {
            try {
                statisticExport(t);
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        });

    }

    public void statisticTask(Task t) {
        TaskDataset dataset = this.taskDataSetRepository.findById(t.getTaskSettings().getDataset_id()).orElse(null);
        if( dataset == null ) {
            log.warn("Dataset not found for task " + t.getId());
            return;
        }
        if( dataset.getMovies_count() == 0 ) {
            Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(t.getTaskSettings().getDataset_id()), t.getTaskSettings().getDataset_id()));
            long l = this.movieDataSetRepository.countByQuery(query);
            dataset.setMovies_count(l);
        }
        statisticMovies(t);
        if( t.getIs_tomo() ) {
            statisticMDoc(t);
        }
        boolean complete = isMovieComplete(t.getMovie_statistic(), dataset);

        if( t.getIs_tomo() ) {
            complete = complete && isMdocComplete(t.getMdoc_statistic(), dataset);
        }
        if( complete ) {
            this.taskService.completeTask(t);
        }
        if( isErrorCompleted(t.getMovie_statistic()) && dataset.getMovie_sync_done() ) {
            var errorComplete = true;
            if( t.getIs_tomo() ) {
                errorComplete = isMdocComplete(t.getMdoc_statistic(), dataset);
            }
            if( errorComplete ) {
                this.taskService.setExportTaskStatus(t.getId(), TaskStatus.stopped);
                this.applicationContext.publishEvent(new TaskStatisticEvent(t));
            }
        }
    }

    public void statisticExport(ExportTaskVo exportTaskVo) {
        TaskDataset dataset = this.taskDataSetRepository.findById(exportTaskVo.getTask().getTaskSettings().getDataset_id()).orElse(null);
        if( dataset == null ) {
            log.warn("Dataset not found for task " + exportTaskVo.getTask().getId());
            return;
        }
        if( dataset.getMovies_count() == 0 ) {
            Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(exportTaskVo.getTask().getTaskSettings().getDataset_id()), exportTaskVo.getTask().getTaskSettings().getDataset_id()));
            long l = this.movieDataSetRepository.countByQuery(query);
            dataset.setMovies_count(l);
        }
        statisticMovieExport(exportTaskVo);
        if( exportTaskVo.getTask().getIs_tomo() ) {
            statisticMdocExport(exportTaskVo);
        }
        ExportTask t = exportTaskVo.getExportTask();
        boolean complete = isMovieComplete(t.getMovie_statistic(), dataset);

        if( exportTaskVo.getTask().getIs_tomo() ) {
            complete = complete && isMovieComplete(t.getMdoc_statistic(), dataset);
        }
        if( complete ) {
            if( !t.isCryosparc() ) {

                this.taskService.completeExportTask(t);
            } else {
                this.cryosparcService.complete(exportTaskVo);
            }
        }
        if( isErrorCompleted(t.getMovie_statistic()) && dataset.getMovie_sync_done() ) {
            var errorComplete = true;
            if( exportTaskVo.getTask().getIs_tomo() ) {
                errorComplete = isMovieComplete(t.getMdoc_statistic(), dataset);
            }
            if( errorComplete ) {
                if( exportTaskVo.getExportTask().getStatus() == TaskStatus.running ) {
                    this.applicationContext.publishEvent(new ExportTaskStatisticEvent(exportTaskVo));
                    this.taskService.setExportTaskStatus(t.getId(), TaskStatus.stopped);
                }

            }
        }
    }


//    private static boolean isCompleted(Task.Statistic movieStatistic) {
//        return movieStatistic != null && Objects.equals(movieStatistic.getTotal(), movieStatistic.getProcessed())
//                && movieStatistic.getTotal() != 0;
//
//    }


    public void statisticMovies(Task t) {
        try {
            Task.Statistic old = t.getMovie_statistic();
            Query q = Query.query(Criteria.where("task_id").is(t.getId()));
            long total = this.movieRepository.countByQuery(q);
            long completed = this.movieRepository.countByQuery(Query.of(q)

                    .addCriteria(Criteria.where("current_step.key").is(HandlerKey.FINISHED)));
            long processing = this.movieRepository.countByQuery(Query.of(q).addCriteria(Criteria.where("process_status.processing").is(true)));

            long error = this.movieRepository.countByQuery(Query.of(q).
                    addCriteria(Criteria.where("error.permanent").is(true)));
            long unprocessed = total - completed - processing - error;
            if( unprocessed < 0 ) {
                unprocessed = 0;
            }
            Task.Statistic statistic = new Task.Statistic(total, completed, unprocessed, processing, error, new Date());
            boolean changed = old == null || !old.equals(statistic);
            if( changed ) {
                t.setMovie_statistic(statistic);
                this.taskRepository.setMovieStatistic(t.getId(), statistic);
            }
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }
    }

    public void statisticMDoc(Task t) {
        try {
            Task.Statistic old = t.getMdoc_statistic();
            Query q = Query.query(Criteria.where("task_id").is(t.getId()));
            long total = this.mDocInstanceRepository.countByQuery(q);
            long completed = this.mDocInstanceRepository.countByQuery(Query.of(q)

                    .addCriteria(Criteria.where("current_step.key").is(HandlerKey.FINISHED)));
            long processing = this.mDocInstanceRepository.countByQuery(Query.of(q).addCriteria(Criteria.where("process_status.processing").is(true)));

            long error = this.mDocInstanceRepository.countByQuery(Query.of(q).
                    addCriteria(Criteria.where("error.permanent").is(true)));
            long unprocessed = total - completed - processing - error;
            if( unprocessed < 0 ) {
                unprocessed = 0;
            }
            Task.Statistic statistic = new Task.Statistic(total, completed, unprocessed, processing, error, new Date());
            boolean changed = old == null || !old.equals(statistic);
            if( changed ) {
                t.setMovie_statistic(statistic);
                this.taskRepository.setMdocStatistic(t.getId(), statistic);
            }
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }
    }


    public void statisticMovieExport(ExportTaskVo exportTaskVo) {
        ExportTask t = exportTaskVo.getExportTask();
        try {
            Task.Statistic old = t.getMovie_statistic();
            Query q = Query.query(Criteria.where("task_id").is(t.getId()));
            long total = this.exportMovieRepository.countByQuery(q);
            long completed = this.exportMovieRepository.countByQuery(Query.of(q)

                    .addCriteria(Criteria.where("current_step.key").is(HandlerKey.FINISHED)));
            long processing = this.exportMovieRepository.countByQuery(Query.of(q).addCriteria(Criteria.where("current_step.key").ne(HandlerKey.INIT)));

            long error = this.exportMovieRepository.countByQuery(Query.of(q).
                    addCriteria(Criteria.where("error.permanent").is(true)));

            long isprocessing = processing - completed - error;

            if( isprocessing < 0 ) {
                isprocessing = 0;
            }
            long unprocessed = total - processing;
            if( unprocessed < 0 ) {
                unprocessed = 0;
            }
            Task.Statistic statistic = new Task.Statistic(total, completed, unprocessed, isprocessing, error, new Date());
            boolean changed = old == null || !old.equals(statistic);
            if( changed ) {
                t.setMovie_statistic(statistic);
                this.exportTaskRepository.setMovieStatistic(t.getId(), statistic);
            }
//            if( !changed && isCompleted(old) && exportTaskVo.getTask().getStatus().isFinished() && (t.getCreated_at() == null || t.getCreated_at().before(DateUtils.addMinutes(new Date(), -10))) ) {
//                this.taskService.completeExportTask(t);
//            }
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }

    }

    public void statisticMdocExport(ExportTaskVo exportTaskVo) {
        ExportTask t = exportTaskVo.getExportTask();
        try {
            Task.Statistic old = t.getMdoc_statistic();
            Query q = Query.query(Criteria.where("task_id").is(t.getId()));
            long total = this.exportMDocInstanceRepository.countByQuery(q);
            long completed = this.exportMDocInstanceRepository.countByQuery(Query.of(q)

                    .addCriteria(Criteria.where("current_step.key").is(HandlerKey.FINISHED)));
            long processing = this.exportMDocInstanceRepository.countByQuery(Query.of(q).addCriteria(Criteria.where("current_step.key").ne(HandlerKey.INIT)));

            long error = this.exportMDocInstanceRepository.countByQuery(Query.of(q).
                    addCriteria(Criteria.where("error.permanent").is(true)));

            long isprocessing = processing - completed - error;

            if( isprocessing < 0 ) {
                isprocessing = 0;
            }
            long unprocessed = total - processing;
            if( unprocessed < 0 ) {
                unprocessed = 0;
            }
            Task.Statistic statistic = new Task.Statistic(total, completed, unprocessed, isprocessing, error, new Date());
            boolean changed = old == null || !old.equals(statistic);
            if( changed ) {
                t.setMovie_statistic(statistic);
                this.exportTaskRepository.setMdocStatistic(t.getId(), statistic);
            }
//            if( !changed && isCompleted(old) && exportTaskVo.getTask().getStatus().isFinished() && (t.getCreated_at() == null || t.getCreated_at().before(DateUtils.addMinutes(new Date(), -10))) ) {
//                this.taskService.completeExportTask(t);
//            }
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }

    }

    public boolean isMovieComplete(Task.Statistic taskStatistic, TaskDataset taskDataset) {
        if( !taskDataset.getMovie_sync_done() ) {
            return false;
        }
        if( taskStatistic.getProcessed() < taskStatistic.getTotal() ) {
            return false;
        }
        if( taskDataset.getMovies_count() == 0 ) {
            Query query = Query.query(Criteria.where("belonging_data")
                    .in(new ObjectId(taskDataset.getId()), taskDataset.getId()));
            long l = this.movieDataSetRepository.countByQuery(query);
            taskDataset.setMovies_count(l);
        }
        return taskStatistic.getProcessed() >= taskDataset.getMovies_count();
    }

    public boolean isMdocComplete(Task.Statistic taskStatistic, TaskDataset taskDataset) {
        if( !taskDataset.getMovie_sync_done() ) {
            return false;
        }
        if( taskStatistic.getProcessed() < taskStatistic.getTotal() ) {
            return false;
        }
        if( taskDataset.getMdoc_count() == 0 ) {
            Query query = Query.query(Criteria.where("belonging_data")
                    .in(new ObjectId(taskDataset.getId()), taskDataset.getId()));
            long l = this.mDocRepository.countByQuery(query);
            taskDataset.setMdoc_count(l);
        }
        return taskStatistic.getProcessed() >= taskDataset.getMdoc_count();
    }


    public boolean isErrorCompleted(Task.Statistic movieStatistic) {
        return movieStatistic.getError() > 0 && (movieStatistic.getProcessed() + movieStatistic.getError() == movieStatistic.getTotal());
    }

    @Override
    public void onApplicationEvent(AppEvent event) {
        if( event instanceof TaskStatusEvent && ((TaskStatusEvent) event).getStatus() != TaskStatus.finished ) {
            Task task = this.taskRepository.findById(((TaskStatusEvent) event).getId()).orElseThrow();
            this.statisticTask(task);
        }
        if( event instanceof ExportTaskStatusEvent exportTaskStatusEvent && exportTaskStatusEvent.getStatus() != TaskStatus.finished ) {
            ExportTask exportTask = this.exportTaskRepository.findById(exportTaskStatusEvent.getId()).orElseThrow();
            Task task = this.taskRepository.findById(exportTask.getTask_id()).orElseThrow();
            statisticExport(new ExportTaskVo(task, exportTask));
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
