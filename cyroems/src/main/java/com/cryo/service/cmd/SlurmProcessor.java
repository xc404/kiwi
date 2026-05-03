package com.cryo.service.cmd;

import com.cryo.common.error.FatalException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Semaphore;

@Slf4j
public class SlurmProcessor
{
    @Getter
    private final String jobId;

    @Getter
    private final SoftwareService.CmdProcess cmdProcess;
    private final Semaphore concurrentLimitLock;
    private boolean locked;
    private final Object lock = new Object();
    @Setter
    private Duration timeout;
    private Date startTime;

    public SlurmProcessor(String jobId, SoftwareService.CmdProcess cmdProcess, Semaphore concurrentLimitLock) {
        this.jobId = jobId;
        this.cmdProcess = cmdProcess;
        this.timeout = Duration.ofSeconds(cmdProcess.getSoftwareConfig().getTimeout());
        this.concurrentLimitLock = concurrentLimitLock;
        this.locked = true;
        this.startTime = new Date();
    }

    public void waitFor() {
        synchronized( lock ) {
            try {
                if( this.timeout != null ) {
                    lock.wait(timeout.toMillis());
                } else {
                    lock.wait();
                }
            } catch( InterruptedException e ) {
                throw new FatalException(e);
            }finally {
                this.cmdProcess.release();
            }
        }
    }

    public void slurmNotify() {
        try {
            this.cmdProcess.release();
        }catch( Exception e ){
        }

        synchronized( lock ){
            if(this.locked){
                try {
                    this.concurrentLimitLock.release();
                    this.locked = false;
                }catch( Exception e  ){
                    log.error(e.getMessage(),e);
                }
            }
            this.lock.notify();
        }

    }

    public boolean isStarted(){
        return startTime.toInstant().isBefore(Instant.now().minus(Duration.ofSeconds(this.cmdProcess.getSoftwareConfig().getStartime())));
    }
//    @Override
//    public void notify(){
//        synchronized( lock ){
//                this.lock.notify();
//        }
//    }


}
