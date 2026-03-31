package com.kiwi.bpmn.component.slurm;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.camunda.bpm.engine.ProcessEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
public class SlurmTaskWatcher
{

    @Autowired
    private SlurmProperties slurmProperties;

    @Autowired
    private ProcessEngine processEngine;
    private final FileAlterationMonitor monitor;
    private volatile boolean running;

    public SlurmTaskWatcher() {
        this.monitor = new FileAlterationMonitor(1000);
    }


    public synchronized void start(){

        try {
            if(running){
                return;
            }
            monitor.start();
            File file = new File(slurmProperties.getSlurmFilePath());
            FileAlterationObserver fileAlterationObserver = FileAlterationObserver.builder().setFile(file)

                    .setFileFilter(new SuffixFileFilter("flag")).get();
            fileAlterationObserver.addListener(new FileWatcher());
            monitor.addObserver(fileAlterationObserver);
            this.running = true;
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

    public class FileWatcher extends  FileAlterationListenerAdaptor
    {


        @Override
        public void onFileCreate(File file) {
            try {
                String taskIdAndWorkId = FileUtils.readFileToString(file);
                String[] split = taskIdAndWorkId.split(",");
                String taskId = split[0];
                String workId = split[1];
                log.info( "Complete external task, taskId: {}, workId: {}", taskId, workId);
                while( true ){
                        try {
                            processEngine.getExternalTaskService().complete(taskId, workId);
                            break;
                        }catch( Exception e ){
                            log.warn("Failed to complete external task, retrying... taskId: {}, workId: {}, error: {}", taskId, workId, e.getMessage());
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            continue;
                        }
                }
                FileUtils.copyFile(file, new File(file.getAbsolutePath() + ".done"));
                FileUtils.deleteQuietly(file);
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
        }
    }


}
