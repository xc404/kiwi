package com.cryo.task.movie;

import com.cryo.dao.InstanceRepository;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.service.MovieService;
import com.cryo.integration.workflow.MovieKiwiWorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.util.StringUtils;
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

/**
 * 单颗粒 movie 任务调度：已废弃在本 JVM 内通过 {@code InstanceProcessor} + {@code IFlow}/{@code ListFlow}
 * 顺序推进；仅负责按批选取 movie 并调用 {@link MovieKiwiWorkflowService} 在 Kiwi 中启动 Camunda 实例。
 */
@Slf4j
public class MovieEngine implements Lifecycle {

    private boolean running = false;
    private final TaskScheduler taskScheduler;
    private final Task task;
    private ScheduledFuture<?> scheduledFuture;
    private final MovieRepository movieRepository;
    private final Duration movieTaskDuration = Duration.ofMinutes(30);
    private final TaskStatistic movieStatisticTask;
    private final MovieService movieService;
    private final MovieSelector movieSelector;
    private final MovieKiwiWorkflowService movieKiwiWorkflowService;
    private final TaskDataSetRepository taskDataSetRepository;

    public MovieEngine(Task task, ApplicationContext applicationContext) {
        this.task = task;
        this.taskScheduler = applicationContext.getBean(TaskScheduler.class);
        this.movieRepository = applicationContext.getBean(MovieRepository.class);
        this.movieStatisticTask = applicationContext.getBean(TaskStatistic.class);
        this.movieService = applicationContext.getBean(MovieService.class);
        this.movieSelector = applicationContext.getBean(MovieSelector.class);
        this.movieKiwiWorkflowService = applicationContext.getBean(MovieKiwiWorkflowService.class);
        this.taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
    }

    @Override
    public void start() {
        if (!movieKiwiWorkflowService.isMoviePipelineReady(task)) {
            throw new IllegalStateException(
                    "Movie 处理已改为仅由 Kiwi Camunda 编排：请配置 app.kiwi.workflow.enabled=true、base-url、"
                            + "access-token（在 Kiwi 个人设置 → 基本设置签发的长期 Bearer Token），并在本 Task 上设置 movieProcessDefinitionId"
                            + "（Kiwi BpmProcess.id），或使用全局 movie-process-definition-id 作为迁移回退。");
        }
        this.running = true;
        this.resetProcessingMovies();
        this.scheduledFuture = this.taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                handle();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
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
        if (!this.running) {
            log.info("Not running");
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            return;
        }

        int batch = movieKiwiWorkflowService.getMovieBatchSize();

        this.movieService.sortMovie(task.getId());
        this.updateProcessingStatus();

        TaskDataset taskDataset = resolveTaskDataset(this.task);

        List<Movie> unprocessedMovies = movieSelector.getHighPriorityMovies(task.getId(), batch);
        if (unprocessedMovies.isEmpty()) {
            if (movieSelector.existOtherHighPriorityMovies(task.getId())) {
                log.info("Other high priority movies exist, skipping");
                return;
            }

            unprocessedMovies = movieSelector.getMidPriorityMovies(task.getId(), batch);

            if (unprocessedMovies.isEmpty()) {

                if (movieSelector.existOtherMidPriorityMovies(task.getId())) {
                    log.info("Other mid priority movies exist, skipping");
                    return;
                }

                unprocessedMovies = this.getUnprocessedMovies(batch);
            }
        }

        for (Movie movie : unprocessedMovies) {
            try {
                movieKiwiWorkflowService.ensureStarted(movie, this.task, taskDataset);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        movieStatisticTask.statisticMovies(task);
    }

    private TaskDataset resolveTaskDataset(Task t) {
        if (t.getTaskSettings() == null || !StringUtils.hasText(t.getTaskSettings().getDataset_id())) {
            return null;
        }
        return taskDataSetRepository.findById(t.getTaskSettings().getDataset_id()).orElse(null);
    }

    private List<Movie> getUnprocessedMovies(int idleCount) {
        if (idleCount == 0) {
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
        this.movieRepository.update(update, query);
        movieStatisticTask.statisticMovies(task);
    }
}
