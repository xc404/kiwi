package com.cryo.task.export;

import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportMDocInstanceRepository;
import com.cryo.model.Task;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExporMDocResult;
import com.cryo.model.export.ExportMDocInstance;
import com.cryo.model.export.ExportTask;
import com.cryo.model.tilt.MDocInstance;
import com.cryo.task.engine.BaseEngine;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.flow.FlowManager;
import com.cryo.task.engine.flow.IFlow;
import com.cryo.task.movie.TaskStatistic;
import com.cryo.task.tilt.MDocContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.Lifecycle;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class ExportMdocEngine extends BaseEngine<ExportMDocInstance, ExporMDocResult> implements Lifecycle
{
    private final ApplicationContext applicationContext;
    private final ExportTask exportTask;
    private boolean running = false;
    private final Task task;
    private final TaskDataSetRepository taskDataSetRepository;
    private final FlowManager flowManager;
    private final TaskStatistic movieStatisticTask;
    private final IFlow flow;
    private final TaskDataset taskDataset;
    private final MDocRepository mDocRepository;

    protected ExportMdocEngine(Task task, ExportTask exportTask, ApplicationContext applicationContext) {
        super(applicationContext.getBean(ExportMDocInstanceRepository.class),
                applicationContext.getBean(ExportProcessor.class), applicationContext.getBean(TaskScheduler.class));
        this.task = task;
        this.exportTask = exportTask;
        this.applicationContext = applicationContext;
        this.flowManager = applicationContext.getBean(FlowManager.class);
        this.movieStatisticTask = applicationContext.getBean(TaskStatistic.class);
        this.taskDataSetRepository = applicationContext.getBean(TaskDataSetRepository.class);
        this.flow = flowManager.getMdocExportFlow(task, exportTask);
        this.taskDataset = this.taskDataSetRepository.findById(this.task.getTaskSettings().getDataset_id()).orElseThrow();
        this.mDocRepository = applicationContext.getBean(MDocRepository.class);
    }


    @Override
    public Context<ExportMDocInstance, ExporMDocResult> createContext(ExportMDocInstance instance) {
        MDoc mDoc = this.mDocRepository.findById(instance.getData_id()).orElseThrow();
        return new MDocExportContext(applicationContext, this.taskDataset, flow, task, exportTask, instance,  mDoc);
    }


    @Override
    public boolean isRunning() {
        return this.running;
    }


    @Override
    public void statistic() {
        movieStatisticTask.statisticMdocExport(new ExportTaskVo(task, exportTask));
    }

    @Override
    protected Criteria belongTo() {
        return Criteria.where("task_id").is(exportTask.getId());
    }


}
