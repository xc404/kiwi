package com.cryo.task.export;

import com.cryo.service.TaskService;
import com.cryo.task.event.ExportTaskStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class ExportMonitorManager implements InitializingBean, ApplicationListener<ExportTaskStatusEvent>, ApplicationContextAware
{

    private final TaskService taskService;
    private final Map<String, ExportMonitor> monitorMap = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;
    private final TaskScheduler taskScheduler;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public synchronized void syncTask() {
        List<ExportTaskVo> runningTasks = this.taskService.getRunningExportTasks();
        Map<String, ExportTaskVo> taskMap = runningTasks.stream().collect(Collectors.toMap(t -> t.getExportTask().getId(), task -> task));
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
        ExportMonitor monitor = this.monitorMap.get(key);
        if( monitor != null ) {
            monitor.stop();
            this.monitorMap.remove(key);
        }
    }

    public synchronized void start(ExportTaskVo task) {

        try {
            ExportMonitor taskMonitor = this.monitorMap.get(task.getExportTask().getId());
            if( taskMonitor == null ) {
                taskMonitor = createTaskMonitor(task);
                taskMonitor.start();
                this.monitorMap.put(task.getExportTask().getId(), taskMonitor);
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
    public void onApplicationEvent(ExportTaskStatusEvent event) {
        syncTask();
    }

    private ExportMonitor createTaskMonitor(ExportTaskVo task) {

        return new ExportMonitor(task, applicationContext);
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
