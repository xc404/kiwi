package com.cryo.task.movie;

import com.cryo.common.error.FatalException;
import com.cryo.dao.InstanceRepository;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Movie;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.service.GainService;
import com.cryo.service.MovieService;
import com.cryo.task.engine.BaseEngine;
import com.cryo.task.engine.InstanceProcessor;
import com.cryo.task.engine.flow.FlowManager;
import com.cryo.task.engine.flow.IFlow;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class MovieEngine  implements Lifecycle
{
    private final ApplicationContext applicationContext;
    private boolean running = false;
    private final InstanceProcessor instanceProcessor;
    private final TaskScheduler taskScheduler;
    private final Task task;
    private ScheduledFuture<?> scheduledFuture;
    private final TaskDataSetRepository taskDataSetRepository;
    private final MovieRepository movieRepository;
    private final FlowManager flowManager;
    private final Duration movieTaskDuration = Duration.ofMinutes(30);
    private final TaskStatistic movieStatisticTask;
    private final MovieService movieService;
    private final MovieSelector movieSelector;
    private final GainService gainTask;
//    private TaskDataset dataset;
    private IFlow flow;

    public MovieEngine(Task task, ApplicationContext applicationContext) {
        this.task = task;
        this.applicationContext = applicationContext;
        this.instanceProcessor = applicationContext.getBean(MovieProcessor.class);
        this.taskScheduler = applicationContext.getBean(TaskScheduler.class);
        this.movieRepository = applicationContext.getBean(MovieRepository.class);
        this.flowManager = applicationContext.getBean(FlowManager.class);
        this.movieStatisticTask = applicationContext.getBean(TaskStatistic.class);
        this.movieService = applicationContext.getBean(MovieService.class);
        this.movieSelector = applicationContext.getBean(MovieSelector.class);
        this.gainTask = applicationContext.getBean(GainService.class);
        this.taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
    }

    @Override
    public void start() {
        this.running = true;
//        this.dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow(() -> {
//            return new FatalException("task dataset not found");
//        });
        this.flow =this.flowManager.getMovieFlow(task);
        this.resetProcessingMovies();
        this.scheduledFuture = this.taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                handle();
            } catch( Exception e ) {
                log.error(e.getMessage());
            }
        }, Duration.ofSeconds(10));
    }

    @Override
    public void stop() {
        scheduledFuture.cancel(true);
        this.resetProcessingMovies();
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    public void handle() {
        if( !this.running ) {
            log.info("Not running");
            return;
        }
        if( Thread.currentThread().isInterrupted() ) {
            return;
        }
        int idleCount = this.instanceProcessor.getIdleCount();
        if( idleCount == 0 ) {
            log.warn("No idle processor, skipping");
            return;
        }

        TaskDataset dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();

        this.movieService.sortMovie(task.getId());
        this.updateProcessingStatus();

        List<Movie> unprocessedMovies = movieSelector.getHighPriorityMovies(task.getId(), idleCount);
        if( unprocessedMovies.isEmpty() ) {
            if( movieSelector.existOtherHighPriorityMovies(task.getId()) ) {
                log.info("Other high priority movies exist, skipping");
                return;
            }

            unprocessedMovies = movieSelector.getMidPriorityMovies(task.getId(), idleCount);

            if( unprocessedMovies.isEmpty() ) {

                if( movieSelector.existOtherMidPriorityMovies(task.getId()) ) {
                    log.info("Other mid priority movies exist, skipping");
                    return;
                }

                unprocessedMovies = this.getUnprocessedMovies(idleCount);
            }
        }

        for( Movie movie : unprocessedMovies ) {
            MovieContext movieContext = new MovieContext(applicationContext,dataset,flow,task,movie);
            while( this.running ) {
                try {
                    this.instanceProcessor.submit(movieContext);
                    break;
                } catch( Exception e ) {
                    try {
                        Thread.sleep(5000);
                    } catch( InterruptedException ex ) {
                        throw new FatalException(ex);
//                        throw new RuntimeException(ex);
                    }
                }
            }
        }
        movieStatisticTask.statisticMovies(task);

    }

    private long countUnprocessedMovies() {
        Query query = Query.query(Criteria.where("task_id").is(task.getId())).addCriteria(InstanceRepository.unprocessed());
        return this.movieRepository.countByQuery(query);
    }

    private List<Movie> getUnprocessedMovies(int idleCount) {
        if( idleCount == 0 ) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("task_id").is(task.getId())).addCriteria(InstanceRepository.unprocessed());
        query.with(Sort.by(Sort.Order.asc("file_create_at")));
        query.limit(idleCount);
        return this.movieRepository.findByQuery(query);
    }

    private void updateProcessingStatus() {
        Date from = Date.from(Instant.now().minus(this.movieTaskDuration));
        Query query = Query.query(Criteria.where("task_id")
                .is(task.getId())
                .and("process_status.processing").is(true)
                .and("process_status.processing_at").lte(from)
        );

        Update update = new Update();
        update.set("process_status.processing", false);
        this.movieRepository.update(update, query);
    }

    private void resetProcessingMovies() {
        Query query = Query.query(Criteria.where("task_id")
                .is(task.getId())
                .and("process_status.processing")
                .is(true)
        );

        Update update = new Update();
        update.set("process_status.processing", false);
//        update.set("current_step", "INIT");
        this.movieRepository.update(update, query);
        movieStatisticTask.statisticMovies(task);
    }


}
