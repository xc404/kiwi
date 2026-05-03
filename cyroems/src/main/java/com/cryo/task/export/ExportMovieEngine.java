package com.cryo.task.export;

import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportMovieResult;
import com.cryo.model.export.ExportTask;
import com.cryo.service.GainService;
import com.cryo.task.engine.BaseEngine;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.flow.FlowManager;
import com.cryo.task.engine.flow.IFlow;
import com.cryo.task.export.cryosparc.CryosparcService;
import com.cryo.task.movie.TaskStatistic;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class ExportMovieEngine extends BaseEngine<ExportMovie, ExportMovieResult> implements Lifecycle
{
    private final ApplicationContext applicationContext;
    private final ExportTaskVo exportTaskVo;
    private boolean running = false;
    private final Task task;
    private final TaskDataSetRepository taskDataSetRepository;
    private final FlowManager flowManager;
    private final TaskStatistic movieStatisticTask;
    private final CryosparcService cryosparcService;
    private IFlow flow;
    private final ExportTask exportTask;
    private TaskDataset taskDataset;
    private ScheduledFuture<?> scheduledFuture;
    private GainService gainService;
    //    private MovieDataSetRepository movieRepository;
    private final TaskStatistic taskStatistic;

    public ExportMovieEngine(Task task, ExportTask exportTask, ApplicationContext applicationContext) {
        super(applicationContext.getBean(ExportMovieRepository.class), applicationContext.getBean(ExportProcessor.class), applicationContext.getBean(TaskScheduler.class));
        this.task = task;
        this.exportTask = exportTask;
        this.applicationContext = applicationContext;
        this.flowManager = applicationContext.getBean(FlowManager.class);
        this.movieStatisticTask = applicationContext.getBean(TaskStatistic.class);
        this.taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.cryosparcService = applicationContext.getBean(CryosparcService.class);
        this.gainService = applicationContext.getBean(GainService.class);
        this.taskStatistic = applicationContext.getBean(TaskStatistic.class);
        this.exportTaskVo = new ExportTaskVo(task, exportTask);
    }

    @Override
    public void start() {
        if( task.isCleaned() ) {
            throw new RuntimeException("task has been cleaned");
        }
        this.running = true;
        init();
        super.start();
        if( this.exportTask.isCryosparc() ) {
            this.scheduledFuture = this.taskScheduler.scheduleWithFixedDelay(() -> {

                if( this.exportTask.getMovie_statistic() != null
                        && this.taskDataset != null && taskStatistic.isMovieComplete(this.exportTask.getMovie_statistic(), taskDataset) ) {
                    log.info("Movie export completed");
                    this.scheduledFuture.cancel(true);
                    return;
                }
                this.cryosparcService.cryosparc(exportTaskVo);
            }, getProcessInternal());
        }
    }

    @Override
    public void stop() {
        if( this.scheduledFuture != null ) {
            this.scheduledFuture.cancel(true);
        }
        super.stop();
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }


//    @Override
//    protected void startProcessor() {
//        if(task.getStatus() == TaskStatus.finished || task.getStatus() == TaskStatus.archived){
//            BatchExportHandler batchExportHandler = new BatchExportHandler(applicationContext, task, exportTask);
//            batchExportHandler.export();
//        }else{
//            super.startProcessor();
//        }
//    }

    private void init() {
        this.taskDataset = this.taskDataSetRepository.findById(this.task.getTaskSettings().getDataset_id()).orElseThrow();
        this.flow = this.flowManager.getMovieExportFlow(task, exportTask);
        if( exportTask.isCryosparc() ) {
            this.cryosparcService.createProject(new ExportTaskVo(task, exportTask));
        }

        TaskDataset.Gain gain = taskDataset.getGain0();

        if( gain == null || StringUtils.isBlank(gain.getUsable_path()) ) {
            log.warn("Gain document for task [{}] is not found", task.getTask_name());
            return;
        }
        exportGain();

    }

    @Override
    protected void beforeProcess() {
        exportGain();
        super.beforeProcess();
    }

    private void exportGain() {
        if( exportTask.getExportSettings().isExportGain() && !exportTask.getGainExported() ) {
            this.gainService.exportGain(exportTask);
        }
    }

    @Override
    protected Context<ExportMovie, ExportMovieResult> createContext(ExportMovie instance) {
        return new MovieExportContext(applicationContext, taskDataset, flow, task, exportTask, instance);
    }

    @Override
    public void statistic() {
        movieStatisticTask.statisticMovieExport(new ExportTaskVo(task, exportTask));
    }

    @Override
    protected Criteria belongTo() {
        return Criteria.where("task_id").is(exportTask.getId());
    }

    @Override
    public void resetProcessing() {
        Query query = Query.query(Criteria.where("cryospacStatus").is(ExportMovie.CryospacStatus.Processing));
        query.addCriteria(belongTo());
        Update update = new Update();
        update.set("cryospacStatus", ExportMovie.CryospacStatus.Init);
        instanceRepository.update(update, query);
        super.resetProcessing();
    }

    @Override
    protected void resetLongProcessing() {
        Date from = Date.from(Instant.now().minus(getTaskDuration()));
        Query query = Query.query(Criteria.where("cryospacStatus")
                .is(ExportMovie.CryospacStatus.Processing).and("process_status.processing_at").lte(from));
        query.addCriteria(belongTo());
        Update update = new Update();
        update.set("cryospacStatus", ExportMovie.CryospacStatus.Init);
        instanceRepository.update(update, query);
        super.resetLongProcessing();
    }

    protected TemporalAmount getTaskDuration() {
        return Duration.ofHours(12);
    }
}
