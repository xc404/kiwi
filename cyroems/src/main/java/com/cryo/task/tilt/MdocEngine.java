package com.cryo.task.tilt;

import com.cryo.common.error.FatalException;
import com.cryo.dao.InstanceRepository;
import com.cryo.dao.MDocInstanceRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.tilt.MDocInstance;
import com.cryo.task.engine.InstanceProcessor;
import com.cryo.task.engine.flow.FlowManager;
import com.cryo.task.engine.flow.IFlow;
import com.cryo.task.movie.TaskStatistic;
import lombok.extern.slf4j.Slf4j;
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
public class MdocEngine implements Lifecycle
{
    private final ApplicationContext applicationContext;
    private final TaskStatistic taskStatistic;
    private boolean running = false;
    private final InstanceProcessor instanceProcessor;
    private final TaskScheduler taskScheduler;
    private final Task task;
    private ScheduledFuture<?> scheduledFuture;
    private final TaskDataSetRepository taskDataSetRepository;
    private final MDocInstanceRepository mDocInstanceRepository;
    private final MDocRepository mDocRepository;
    private final FlowManager flowManager;
    private final Duration mdocTaskDuration = Duration.ofMinutes(30);
    //    private final TaskStatistic movieStatisticTask;
//    private final MovieService movieService;
//    private final MovieSelector movieSelector;
//    private TaskDataset dataset;
    private IFlow flow;

    public MdocEngine(Task task, ApplicationContext applicationContext) {

        this.task = task;
        this.applicationContext = applicationContext;
        this.instanceProcessor = applicationContext.getBean(MDocProcessor.class);
        this.taskScheduler = applicationContext.getBean(TaskScheduler.class);
        this.mDocInstanceRepository = applicationContext.getBean(MDocInstanceRepository.class);
        this.flowManager = applicationContext.getBean(FlowManager.class);
        this.taskStatistic = applicationContext.getBean(TaskStatistic.class);
//        this.movieService = applicationContext.getBean(MovieService.class);
//        this.movieSelector = applicationContext.getBean(MovieSelector.class);
        this.taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.mDocRepository = applicationContext.getBean(MDocRepository.class);
    }

    @Override
    public void start() {
        this.running = true;
        this.flow = this.flowManager.getMDocFlow(task);
        this.resetProcessing();
        this.scheduledFuture = this.taskScheduler.scheduleWithFixedDelay(() -> {
            try {
                handle(task);
            } catch( Exception e ) {
                log.error(e.getMessage());
            }
        }, Duration.ofSeconds(10));
    }

    @Override
    public void stop() {
        scheduledFuture.cancel(true);
        this.resetProcessing();
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    public void handle(Task task) {
        if( !this.running ) {
            log.info("Not running");
            return;
        }
        if( task == null ) {
            log.warn("Received null task, skipping");
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
        TaskDataset taskDataset = this.taskDataSetRepository.findById(this.task.getTaskSettings().getDataset_id()).orElseThrow();
        idleCount = idleCount + 30;


        this.updateProcessingStatus();

        List<MDocInstance> unprocessedMovies = this.getUnprocessedMovies(idleCount);
        if(unprocessedMovies.isEmpty()){
            log.info("No unprocessed movies");
            return;
        }
        for( MDocInstance instance : unprocessedMovies ) {
            MDoc mDoc = this.mDocRepository.findById(instance.getData_id()).orElseThrow();
            MDocContext movieContext = new MDocContext(applicationContext, taskDataset, flow, task, instance, mDoc);
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
        taskStatistic.statisticMDoc(task);

    }


    private List<MDocInstance> getUnprocessedMovies(int idleCount) {
        if( idleCount == 0 ) {
            return List.of();
        }
        Query query = Query.query(Criteria.where("task_id").is(task.getId())).addCriteria(InstanceRepository.unprocessed());
        query.with(Sort.by(Sort.Order.asc("file_create_at")));
        query.limit(idleCount);
        return this.mDocInstanceRepository.findByQuery(query);
    }

    private void updateProcessingStatus() {
        Date from = Date.from(Instant.now().minus(this.mdocTaskDuration));
        Query query = Query.query(Criteria.where("task_id")
                .is(task.getId())
                .and("process_status.processing").is(true)
                .and("process_status.processing_at").lte(from)
        );

        Update update = new Update();
        update.set("process_status.processing", false);
        this.mDocInstanceRepository.update(update, query);
    }

    private void resetProcessing() {
        Query query = Query.query(Criteria.where("task_id")
                .is(task.getId())
                .and("process_status.processing")
                .is(true)
        );

        Update update = new Update();
        update.set("process_status.processing", false);
//        update.set("current_step", "INIT");
        this.mDocInstanceRepository.update(update, query);
        taskStatistic.statisticMDoc(task);
    }


}
