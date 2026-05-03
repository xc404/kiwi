package com.cryo.task.export.cryosparc;

import cn.hutool.core.thread.BlockPolicy;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.export.ExportTask;
import com.cryo.service.CryosparcProjectService;
import com.cryo.service.FilePathService;
import com.cryo.service.TaskService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.event.AppEvent;
import com.cryo.task.export.ExportTaskVo;
import com.cryo.task.support.ExportSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryosparcService implements ApplicationListener<AppEvent>, InitializingBean, ApplicationContextAware
{
    private final ExportTaskRepository exportTaskRepository;
    private final TaskRepository taskRepository;
    private final SoftwareService softwareService;
    private final FilePathService filePathService;
    private final PatchCryosparc patchCryosparc;
    private final ExportSupport exportSupport;
    //    private final CryosparcCompleteService cryosparcCompleteService;
    private ThreadPoolTaskExecutor executor;
    @Value("${app.cryosparc.pool_size:10}")
    private int maxPoolSize = 10;
    //    @Value("${app.cryosparc.output_dir:/home/cryosparc-data}")
//    private String output_dir = "/home/cryosparc-data";
    private ApplicationContext applicationContext;
    private final TaskService taskService;
    private final CryosparcProjectService cryosparcProjectService;
    private Map<String, ExportTask> runningTasks = new ConcurrentHashMap<>(maxPoolSize, 1000 * 60 * 60 * 24);

    public synchronized void createProject(ExportTaskVo exportTask) {
        ExportTask task = exportTask.getExportTask();
//        Task task = movieContext.getTask();
        if( task.getCryosparcProject() != null ) {
            return;
        }
        CryosparcProject projectAndWorkspace = cryosparcProjectService.createProjectAndWorkspace(exportTask);
        task.setCryosparcProject(projectAndWorkspace);
        exportTaskRepository.save(task);
    }

    public void cryosparc(ExportTaskVo task) {
        this.executor.submit(() -> {
            try {
                this.patchCryosparc.handle(task);
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        });
    }

    public void complete(ExportTaskVo task) {
        this.complete(task, false);
    }

    public void complete(ExportTaskVo task, boolean force) {
        if( runningTasks.containsKey(task.getExportTask().getId()) ) {
            log.info("running");
            return;
        }
        runningTasks.put(task.getExportTask().getId(), task.getExportTask());
        ExportTask exportTask = task.getExportTask();
        CryosparcCompleteHanlder cryosparcCompleteHanlder = new CryosparcCompleteHanlder(applicationContext, task.getTask(), task.getExportTask());
        synchronized( task.getExportTask().getId().intern() ) {
            if( !exportTask.isCryosparc() ) {
                return;
            }
            exportTask = this.exportTaskRepository.findById(task.getExportTask().getId()).orElseThrow();
            if( exportTask.getCryosparcCompleteStatus() != null ) {
                if( !force ) {
                    log.info("cryosparc complete");
                    return;
                }
            }
            cryosparcCompleteHanlder.init();
        }
        ExportTask finalExportTask = exportTask;
        this.executor.execute(() -> {

            try {

                cryosparcCompleteHanlder.complete();
                this.taskService.completeExportTask(finalExportTask);
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
            runningTasks.remove(finalExportTask.getId());
        });
    }

    @Override
    public void onApplicationEvent(AppEvent event) {
//        if(event instanceof ExportTaskStatusEvent){
//            if(((ExportTaskStatusEvent) event).getStatus() == TaskStatus.finished){
//                ExportTask exportTask = this.exportTaskRepository.findById(((ExportTaskStatusEvent) event).getId()).orElseThrow();
//                Task task = this.taskRepository.findById(exportTask.getTask_id()).orElseThrow();
////                if(task.getStatus() == TaskStatus.running){
//                    this.complete(new ExportTaskVo(task,exportTask));
////                }
//            }
//        }
//        if(event instanceof TaskStatusEvent ){
//            if(((TaskStatusEvent) event).getStatus() == TaskStatus.finished){
//                Task task = this.taskRepository.findById(((TaskStatusEvent) event).getId()).orElseThrow();
//                List<ExportTask> exportTasks = this.exportTaskRepository.findByTaskId(((TaskStatusEvent) event).getId());
//                for(ExportTask exportTask : exportTasks) {
////                    if( exportTask.getStatus() == TaskStatus.running ) {
//                        this.complete(new ExportTaskVo(task,exportTask));
////                    }
//                }
//            }
//        }

    }

//    private void onExportTaskComplete(ExportTaskVo ) {
//        if( event.getStatus() == TaskStatus.finished ) {
//            ExportTask exportTask = this.exportTaskRepository.findById(event.getId()).orElseThrow();
//            Task task = this.taskRepository.findById(exportTask.getTask_id()).orElseThrow();
//
//            this.complete(new ExportTaskVo(task,exportTask));
//        }
//    }

    @Override
    public void afterPropertiesSet() throws Exception {
//        PriorityBlockingQueue<Object> objects = new PriorityBlockingQueue<>();
//        objects.
        executor = new ThreadPoolTaskExecutor();
        int maxPoolSize = this.maxPoolSize;
        executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("cryosparc");
        executor.setRejectedExecutionHandler(new BlockPolicy());
        executor.setCorePoolSize(maxPoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(0);
        executor.setThreadPriority(Thread.MAX_PRIORITY);
        executor.initialize();

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
