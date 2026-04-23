package com.cryo.task.filedetect;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.TaskRepository;
import com.cryo.model.Task;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.SuffixFileFilter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.context.Lifecycle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.StreamSupport;

@Slf4j
public class TaskFileDetector extends FileAlterationListenerAdaptor implements FileAlterationListener, Lifecycle
{

    private static final FileAlterationMonitor fileAlterationMonitor = new FileAlterationMonitor();
    static {
        try {
            fileAlterationMonitor.start();
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }
    private static final List<String> suffix;

    static {
        suffix = new ArrayList<>();
        suffix.addAll(Arrays.stream(GainFileHandler.GainFileSuffix).toList());
        suffix.addAll(Arrays.stream(MovieFileHandler.MovieFileSuffix).toList());
    }

    private final TaskRepository taskRepository;
    private final Task task;
    private final List<TaskFileHandler> handlers;
    //    private final List<String> supportedSuffix;
    private final FileAlterationObserver fileAlterationObserver;

    public TaskFileDetector(TaskRepository taskRepository, List<TaskFileHandler> handlers, Task task) {
        this.taskRepository = taskRepository;
        this.task = task;
        this.handlers = handlers;
        try {
            this.fileAlterationObserver = FileAlterationObserver.builder().setFile(task.getInput_dir())

                    .setFileFilter(new SuffixFileFilter(suffix.toArray(new String[0])))
                    .get()

            ;
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
//        this.supportedSuffix = supportedSuffix;
    }

    public void initDetect() {
        File file = new File(task.getInput_dir());
        FileUtils.listFiles(file, suffix.toArray(new String[0]), true)
                .forEach(this::onFileCreate);
        this.taskRepository.updateLastDetectTime(task.getId(), new Date());

    }

    @Override
    public void onStop(FileAlterationObserver observer) {
        super.onStop(observer);
        this.taskRepository.updateLastDetectTime(task.getId(), new Date());
    }


    @Override
    public void onFileCreate(File file) {
//        Date lastModified = com.cryo.common.utils.FileUtils.lastModified(file);
//        if( task.getLast_file_detect_time() != null && lastModified.before(task.getLast_file_detect_time()) ) {
//            log.info("ignore file {} change, file has been handled, detect time {} , file create at {}", file, task.getLast_file_detect_time(), lastModified);
//            return;
//        }
        String suffix = FileNameUtil.getSuffix(file);
        TaskFileHandler fileHandler = this.handlers.stream().filter(f -> {
            return f.support(suffix);
        }).findFirst().orElse(null);
        if( fileHandler == null ) {
            log.info("not handler found for file {}", file);
        } else {
            log.info("file {} change", file);
            fileHandler.handle(task, file);
        }
    }

    public synchronized void start() {
        try {

            this.initDetect();
        } catch( Exception e ) {
            log.error(e.getMessage(), e);
        }
        fileAlterationObserver.addListener(this);
        fileAlterationMonitor.addObserver(fileAlterationObserver);
    }

    public void stop() {
        fileAlterationObserver.removeListener(this);
        fileAlterationMonitor.removeObserver(fileAlterationObserver);
    }

    @Override
    public boolean isRunning() {
        return StreamSupport.stream(fileAlterationMonitor.getObservers().spliterator(), false).anyMatch(f -> f == this.fileAlterationObserver);
    }

}
