package com.cryo.task.engine;

import com.cryo.dao.InstanceRepository;
import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public abstract class BaseEngine<T extends Instance, R extends InstanceResult>
{
    protected final InstanceRepository<T> instanceRepository;
    protected final InstanceProcessor instanceProcessor;
    protected final TaskScheduler taskScheduler;
    protected ScheduledFuture<?> processFuture;
    private ScheduledFuture<?> asyncProcessFuture;

    protected BaseEngine(InstanceRepository<T> instanceRepository, InstanceProcessor instanceProcessor, TaskScheduler taskScheduler) {
        this.instanceRepository = instanceRepository;
        this.instanceProcessor = instanceProcessor;
        this.taskScheduler = taskScheduler;
    }


    public void start(){

        resetProcessing();
        startProcessor();
    }

    public void stop(){
        stopProcessor();
        resetProcessing();
    }

    protected void startProcessor(){
        log.info("Starting Processing");
        this.processFuture = this.taskScheduler.scheduleWithFixedDelay(this::process, getProcessInternal());
        this.asyncProcessFuture = this.taskScheduler.scheduleWithFixedDelay(this::processAsync, getProcessInternal());
    }


    protected void stopProcessor(){
        log.info("Stopping Processing");
        if(this.processFuture != null){
            this.processFuture.cancel(true);
        }
        if(this.asyncProcessFuture != null){
            this.asyncProcessFuture.cancel(true);
        }
    }


    protected void process(){
        resetLongProcessing();
        List<T> unprocessedInstances = getUnprocessedInstances();
        log.debug("Processing {} instances", unprocessedInstances.size());
        processInstances(unprocessedInstances);
    }


    protected void processAsync(){
        List<T> waitingInstances = getWaitingInstances();
        log.debug("Processing  {} waiting instances", waitingInstances.size());
        processInstances(waitingInstances);
    }


    protected Duration getProcessInternal() {
        return Duration.ofSeconds(10);
    }

    protected void processInstances(List<T> instances){
        beforeProcess();
        List<Context<T, R>> contexts = instances.stream().map(instance -> {
            return this.createContext(instance);
        }).toList();
        this.instanceProcessor.submitBatch(contexts);
        afterProcess();
    }

    protected void afterProcess() {
        this.statistic();
    }



    protected void beforeProcess(){

    }

    protected List<T> getWaitingInstances(){
        Query query = Query.query(InstanceRepository.waiting());
        query.addCriteria(belongTo());
        return instanceRepository.findByQuery(query);
    }

    protected List<T> getUnprocessedInstances() {
        Query query = Query.query(InstanceRepository.unprocessed());
        query.addCriteria(belongTo());
        query.limit(instanceProcessor.getIdleCount()*2);
        return instanceRepository.findByQuery(query);
    }

    protected abstract Context<T,R> createContext(T instance);

    public abstract void statistic() ;

    public void resetProcessing(){
        Query query = Query.query(InstanceRepository.processing());
        query.addCriteria(belongTo());
        Update update = new Update();
        update.set("process_status.processing", false);
        instanceRepository.update( update,query);
        statistic();
    }

    protected void resetLongProcessing() {
        Date from = Date.from(Instant.now().minus(getTaskDuration()));
        Query query = Query.query(belongTo().and("process_status.processing").is(true)
                .and("process_status.processing_at").lte(from));

        Update update = new Update();
        update.set("process_status.processing", false);
        this.instanceRepository.update(update, query);
    }

    protected TemporalAmount getTaskDuration() {
        return Duration.ofMinutes(30);
    }

    abstract protected Criteria belongTo();
}
