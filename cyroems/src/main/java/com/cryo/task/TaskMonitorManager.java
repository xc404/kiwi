package com.cryo.task;

import com.cryo.dao.TaskRepository;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.service.TaskService;
import com.cryo.task.event.TaskStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@ConditionalOnProperty(name = "app.task.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskMonitorManager implements InitializingBean, ApplicationListener<TaskStatusEvent>, ApplicationContextAware
{

    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final Map<String, TaskMonitor> monitorMap = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;
    private final TaskScheduler taskScheduler;
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public synchronized void syncTask() {
        List<Task> runningTasks = this.taskService.getRunningTasks();
        Map<String, Task> taskMap = runningTasks.stream().collect(Collectors.toMap(Task::getId, task -> task));
        Set<String> newKeySet = taskMap.keySet();
        Set<String> oldKeySet = this.monitorMap.keySet();
        Set<String> newKeys = new HashSet<>(newKeySet);
        newKeys.removeAll(oldKeySet);

        Set<String> deleteKeys = new HashSet<>(oldKeySet);
        deleteKeys.removeAll(newKeySet);

        deleteKeys.forEach(this::stopTask);
        newKeys.forEach(key -> start(taskMap.get(key)));
    }

    public synchronized void stopTask(String key) {
        TaskMonitor monitor = this.monitorMap.get(key);
        if( monitor != null ) {
            monitor.stop();
            this.monitorMap.remove(key);
        }
    }

    public synchronized void start(Task task) {

        try {
            TaskMonitor taskMonitor = this.monitorMap.get(task.getId());
            if( taskMonitor == null ) {
                taskMonitor = createTaskMonitor(task);
                taskMonitor.start();
                this.monitorMap.put(task.getId(), taskMonitor);
            }

        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        this.taskScheduler.schedule(this::syncTask, Instant.now().plus(Duration.ofSeconds(10)));
    }


    @Override
    public void onApplicationEvent(TaskStatusEvent event) {
        if( event.getStatus() == TaskStatus.running ) {
            Optional<Task> task = this.taskRepository.findById(event.getId());
            task.ifPresent(t -> {
                if( t.getStatus() == TaskStatus.running ) {
                    start(t);
                }
            });
        }
        if( event.getStatus() != TaskStatus.running ) {
            stopTask(event.getId());
        }
    }

    private TaskMonitor createTaskMonitor(Task task) {
        return new TaskMonitor(task, applicationContext);
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
