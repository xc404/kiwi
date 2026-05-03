package com.cryo.service;


import com.cryo.dao.TaskRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.export.ExportTask;
import com.cryo.task.event.ExportTaskStatusEvent;
import com.cryo.task.event.TaskStatusEvent;
import com.cryo.task.export.ExportTaskVo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService implements ApplicationContextAware, InitializingBean
{

    private final TaskRepository taskRepository;
    private final ExportTaskRepository exportTaskRepository;
    private ApplicationContext applicationContext;
    @Getter
    private List<Task> runningTasks = new ArrayList<>();

    private List<ExportTask> runningExportTasks = new ArrayList<>();
    private final GainService gainService;

    public Task setTaskStatus(String id, TaskStatus status) {
        Task task = this.taskRepository.findById(id).orElse(null);
        if( task == null ) {
            throw new RuntimeException("task not exist");
        }
        task.setStatus(status);
        this.taskRepository.save(task);
        this.syncTasks();

        publishEvent(id, status);
        return task;
    }

    public ExportTask setExportTaskStatus(String id, TaskStatus status) {
        ExportTask task = this.exportTaskRepository.findById(id).orElse(null);
        if( task == null ) {
            throw new RuntimeException("task not exist");
        }
        task.setStatus(status);
        this.exportTaskRepository.save(task);
        this.loadRunningExportTasks();

        publishExportTaskEvent(id, status);
        return task;
    }

    @Async
    private void publishEvent(String id, TaskStatus status) {
        this.applicationContext.publishEvent(new TaskStatusEvent(id, status));
    }

    @Async
    private void publishExportTaskEvent(String id, TaskStatus status) {
        this.applicationContext.publishEvent(new ExportTaskStatusEvent(id, status));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void loadRunningTasks() {
        this.runningTasks = this.taskRepository.getRunningTasks();
    }


    public void loadRunningExportTasks() {
//        List<String> taskIds = this.runningTasks.stream().map(t -> t.getId()).toList();
        Query query = Query.query(Criteria.where("status").is(TaskStatus.running));
        this.runningExportTasks = this.exportTaskRepository.findByQuery(query);
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void syncTasks() {
        loadRunningTasks();
        loadRunningExportTasks();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        syncTasks();
    }


    public void completeTask(Task task) {
        this.setTaskStatus(task.getId(), TaskStatus.finished);
    }


    public List<ExportTaskVo> getRunningExportTasks() {
        List<String> taskIds = this.runningExportTasks.stream().map(t -> t.getTask_id()).toList();
        List<Task> tasks = this.taskRepository.findAllById(taskIds);

        Map<String, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, task -> task));
        return this.runningExportTasks.stream().map(task -> new ExportTaskVo(taskMap.get(task.getTask_id()), task))
                .filter(runningExportTask -> runningExportTask.getTask() != null).toList();
    }

    public void completeExportTask(ExportTask t) {
        if( t.getExportSettings().isExportGain() && !t.getGainExported() ) {
            this.gainService.exportGain(t);
        }
        this.setExportTaskStatus(t.getId(), TaskStatus.finished);
    }
}
