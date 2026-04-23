package com.cryo.service.cmd;

import com.cryo.common.error.FatalException;
import com.cryo.common.error.RetryException;
import com.cryo.model.TaskStatus;
import com.cryo.task.event.TaskStatusEvent;
import com.cryo.task.engine.Context;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.StringUtil;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SlurmService implements ExpirationListener<String, SlurmProcessor>, ApplicationListener<TaskStatusEvent>, ApplicationContextAware, InitializingBean
{
    private final ExpiringMap<String, SlurmProcessor> expiringMap;

    @Value("${app.task.slurm.concurrentLimit:100}")
    private int concurrentLimit = 100;


    @Value("${app.task.slurm.submitLimit:10}")
    private int submitLimit = 30;


    private Thread slurmJobTracer;

    //防止提交并行太快，最多50个并行
    private Semaphore concurrentLimitLock;
    private Semaphore submitLimitLock;


    private ApplicationContext applicationContext;

    public SlurmService() {
        this.expiringMap = ExpiringMap.builder()
                .expiration(1, TimeUnit.MINUTES)
                .variableExpiration()
                .expirationListener(this).build();
        slurmJobTracer = new Thread(new SlurmJobTracer());
        slurmJobTracer.setName("SlurmJobTracer");
        slurmJobTracer.start();
    }


    public SlurmProcessor start(SoftwareService.CmdProcess cmdProcess) {
        try {
            submitLimitLock.acquire();
        } catch( InterruptedException e ) {
            log.error(e.getMessage(), e);
            throw new RetryException(e);
        }
        try {
            try {
                boolean locked = this.concurrentLimitLock.tryAcquire(cmdProcess.getSoftwareConfig().getTimeout(), TimeUnit.SECONDS);
                if(!locked){
                    log.warn("Failed to acquire pool lock for command: {}", this);
                    throw new RetryException("Unable to acquire pool lock");
                }
            } catch( InterruptedException e ) {
                log.error(e.getMessage(),e);
                throw new FatalException(e);
            }
            String jobId;
            boolean locked = cmdProcess.tryLock();
            if(!locked){
                log.warn("Failed to acquire pool lock for command: {}", this);
                throw new RetryException("Unable to acquire pool lock");
            }
            SlurmProcessor slurmProcessor = null;
            try {
                cmdProcess._startAndWait();
                String output = cmdProcess.result();
                jobId = parseJobId(output);
                if( StringUtil.isBlank(jobId) || !NumberUtils.isDigits(jobId) ) {
                    throw new CmdException("Slurm no job id :" + output);
                }
                cmdProcess.setJobId(jobId);
                slurmProcessor = new SlurmProcessor(jobId, cmdProcess, this.concurrentLimitLock);
                synchronized( this.expiringMap ){
                    expiringMap.put(jobId, slurmProcessor, cmdProcess.getSoftwareConfig().getTimeout(), TimeUnit.SECONDS);
                }
                log.info("job {} is started, current job size {}", jobId,this.expiringMap.size());
                return slurmProcessor;

            } catch( Throwable e ) {
                log.error(e.getMessage(), e);
                cmdProcess.release();
                this.concurrentLimitLock.release();
                throw e;
            }

        } finally {
            submitLimitLock.release();
        }
    }

    private String parseJobId(String output) {
        String[] split = output.lines().toList().get(0).split(" +");
        return StringUtils.trim(split[split.length - 1]);
    }

    public  void completeJob(String jobId) {
        SlurmProcessor slurmProcessor = this.expiringMap.get(jobId);
        if( slurmProcessor != null && slurmProcessor.isStarted()) {
            log.info("job {} is complete, current job size {}", jobId,this.expiringMap.size());
            slurmProcessor.slurmNotify();
            this.expiringMap.remove(jobId);
        }
    }

//    private void complete(SlurmProcessor remove) {
//        remove.slurmNotify();
//        try {
//            this.concurrentLimitLock.release();
//        }catch( Exception e ){
//            log.error(e.getMessage(),e);
//        }
//    }

    @Override
    public void expired(String key, SlurmProcessor value) {
        log.warn("job {} is expired", key);
        value.slurmNotify();
    }

    public void compare(List<String> jobIds) {
        Set<String> all = null;
        synchronized( this.expiringMap ){
            all = new HashSet<>(this.expiringMap.keySet());
            jobIds.forEach(all::remove);
        }
        all.forEach(this::completeJob);
    }

//    @Autowired
//    public void setSoftwareService(SoftwareService softwareService) {
//        this.softwareService = softwareService;
//    }

    @Override
    public void onApplicationEvent(TaskStatusEvent event) {
        TaskStatus status = event.getStatus();
        String taskId = event.getId();
        if( status != TaskStatus.running ) {
            cancelTask(taskId);
        }
    }

    private void cancelTask(String taskId) {
        List<String> jobIds = this.expiringMap.entrySet().stream().filter((entry) -> {
            SlurmProcessor value = entry.getValue();
            Context movieContext = value.getCmdProcess().getContext();
            if( movieContext == null ) {
                return false;
            }
            return movieContext.getTask().getId().equals(taskId);
        }).map(entry -> entry.getKey()).toList();
//        cancelJobs(jobIds);
    }

    private void cancelJobs(List<String> jobIds) {
        if( jobIds.isEmpty() ) {
            return;
        }
        this.getSoftwareService().scancel(jobIds).startAndWait();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
//        this.softwareService = applicationContext.getBean(SoftwareService.class);
        this.concurrentLimitLock = new Semaphore(this.concurrentLimit);
        this.submitLimitLock = new Semaphore(this.submitLimit);
    }

    public SoftwareService getSoftwareService() {
        return applicationContext.getBean(SoftwareService.class);
    }

    public class SlurmJobTracer implements Runnable
    {

        @Override
        public void run() {
            while( true ) {
                try {
                    Thread.sleep(10000);
                } catch( InterruptedException e ) {
                    log.error(e.getMessage(), e);
                }
                try {
                    if( expiringMap.isEmpty() ) {
                        continue;
                    }
                    List<String> jobIds = getJobIds();
                    compare(jobIds);
                } catch( Exception e ) {
                    log.error(e.getMessage(), e);
                }
            }
        }

        private List<String> getJobIds() {
            SoftwareService.CmdProcess squeue = getSoftwareService().squeue();
            squeue.startAndWait();
            return squeue.result().lines().map(line -> {
                String[] split = StringUtils.trim(line).split(" +");
                return split[0];
            }).toList();
        }
    }


}
