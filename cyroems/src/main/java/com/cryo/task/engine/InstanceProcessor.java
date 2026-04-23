package com.cryo.task.engine;

import cn.hutool.core.thread.BlockPolicy;
import com.cryo.common.error.FatalException;
import com.cryo.common.mongo.MongoTemplate;
import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import com.cryo.model.Movie;
import com.cryo.model.TaskStatus;
import com.cryo.task.event.TaskStatusEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.result.R;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.slf4j.MDC;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

//@Service
@Slf4j
@RequiredArgsConstructor
public class InstanceProcessor implements InitializingBean, ApplicationListener<TaskStatusEvent>
{

    public static final String INSTANCE_LOG_ID = "INSTANCE_LOG_ID";
    //    private final InstanceRepository<T> movieRepository;
//    private final com.cryo.dao.InstanceResultRepository<K> movieResultRepository;
    private ThreadPoolTaskExecutor highExecutor;
    private ThreadPoolTaskExecutor lowExecutor;
    //    @Value("${app.task.movie.maxPoolSize:100}")
//    private int maxPoolSize = 100;
    private final Handlers movieDispatcher;

    private ThreadPoolTaskExecutor asyncTAskExecutor;

    private final Map<MovieWorker, CompletableFuture<?>> runningMovies = new ConcurrentHashMap<>();
    private final MongoTemplate mongoTemplate;

    @Getter
    @Value("${app.task.movie.max_error_limit:10}")
    private final int maxErrorCount = 10;


    public <T extends Instance, K extends InstanceResult> void submitBatch(List<Context<T, K>> contextList) {

        try {
            CompletableFuture.allOf(
                    contextList.stream().map(context -> this.submit(context)).toArray(CompletableFuture[]::new)).get();
        } catch( InterruptedException | ExecutionException e ) {
            log.error(e.getMessage(), e);
        }
    }

    public <T extends Instance, K extends InstanceResult> CompletionStage<Void> submit(Context<T, K> context) {
        Instance movie = context.getInstance();
        TaskStep nextStep = context.getFlow().next(context, movie.getCurrent_step());
        movie.getProcess_status().setProcessing(true);
        if( nextStep != null && !nextStep.getKey().isAsync() || movie.getProcess_status().getProcessing_at() == null ) {
            movie.getProcess_status().setProcessing_at(new Date());
        }
        this.mongoTemplate.save(movie);
        MovieWorker<T, K> movieWorker = new MovieWorker<>(context);

        CompletableFuture<Void> submit;
        if( nextStep != null && nextStep.getKey().getPriority() == HandlerKey.Priority.High ) {
            log.info("Submit movie to high executor, queue size: {}", highExecutor.getQueueSize());
            submit = highExecutor.submitCompletable(movieWorker);
        } else if( nextStep != null && nextStep.getKey().isAsync() ) {
            log.info("Submit movie to async executor, queue size: {}", asyncTAskExecutor.getQueueSize());
            submit = asyncTAskExecutor.submitCompletable(movieWorker);
        } else {
            log.info("Submit movie to low executor, queue size: {}", lowExecutor.getQueueSize());
            submit = lowExecutor.submitCompletable(movieWorker);
        }

        runningMovies.put(movieWorker, submit);
        return submit.handle((r, ex) -> {
            runningMovies.remove(movieWorker);
            if( ex == null ) {
                return (Void) r;
            }
            log.error("Movie worker error: {0}", ex);
            return null;
        });
    }


    public <T extends Instance, K extends InstanceResult> R<StepOutput> step(Context<T, K> context) {
        return this._next(context, false);
    }

    public <T extends Instance, K extends InstanceResult> R<StepOutput> next(Context<T, K> context) {

        return this._next(context, true);
    }

    private <T extends Instance, K extends InstanceResult> R<StepOutput> _next(Context<T, K> context, boolean autoNext) {
        T movie = context.getInstance();

        if( Thread.currentThread().isInterrupted() ) {
            log.warn("{} Thread canceled", movie.getId());
            return R.fail("Thread canceled");
        }
        context.start();
        String logId = context.getTask().getId() + "_" + movie.getName();
        try {
            MDC.put(INSTANCE_LOG_ID, logId);


            TaskStep currentStep = movie.getCurrent_step();

            // 如果当前步骤已经是 FINISHED，直接返回成功结果
            if( currentStep.getKey() == HandlerKey.FINISHED ) {
                return R.success(StepOutput.step(new Date(), currentStep, StepResult.success("Finished")));
            }
            int steps = 0;
            Date startTime = new Date();
            TaskStep nextStep = null;
            try {
                while( true ) {
                    if( Thread.currentThread().isInterrupted() ) {
                        log.warn("{} Thread canceled", movie.getId());
                        return R.fail("Thread canceled");
                    }
                    nextStep = context.getFlow().next(context, currentStep);

                    // 校验 nextStep 是否合法
                    if( nextStep == null || nextStep == currentStep ) {
                        throw new IllegalArgumentException("Next step is null for movie: " + movie.getId());
                    }

                    // 如果下一步是 FINISHED，结束流程
                    if( nextStep.getKey() == HandlerKey.FINISHED ) {
                        StepOutput stepOutput = StepOutput.step(new Date(), TaskStep.of(HandlerKey.FINISHED), StepResult.success("Finished"));
                        movie.addStep(stepOutput);
                        movie.setCurrent_step(nextStep);
                        movie.setStatus(R.success());
                        movie.getProcess_status().setProcessing(false);
                        movie.setForceReset(false);
                        this.mongoTemplate.save(movie);
                        return R.success(stepOutput);
                    }

                    if( steps != 0 && nextStep.getKey().getPriority() != currentStep.getKey().getPriority() ) {
                        log.info("Movie [{}] next step [{}] priority [{}] is not equal to current step [{}] priority [{}]",
                                movie.getId(), nextStep, nextStep.getKey().getPriority(), currentStep, currentStep.getKey().getPriority());
                        try {
                            movie.setCurrent_step(currentStep);
                            this.submit(context);
                        } catch( RejectedExecutionException e ) {
                            movie.getProcess_status().setProcessing(false);
                            this.mongoTemplate.save(movie);
                            log.error(e.getMessage(), e);
                        }
                        return R.success();
                    }


                    startTime = new Date();
                    // 处理当前步骤
                    StepResult output = handle(context, nextStep);
                    log.debug("Movie step: {} output: {}", nextStep, output.getMessage());
                    StepOutput stepOutput = StepOutput.step(startTime, nextStep, output);
                    movie.addStep(stepOutput);
                    if( stepOutput.getResult().isWaitCondition() ) {
                        movie.setWaiting(true);
                        movie.setStatus(R.status(true, stepOutput.getResult().getMessage()));
                        this.mongoTemplate.save(movie);
                        return R.success(stepOutput);
                    } else {
                        movie.setWaiting(false);
                    }
                    if( !output.isSuccess() ) {
                        saveError(movie, stepOutput);
                        return R.fail(stepOutput.getResult().getMessage());
                    }

                    // 更新状态
                    currentStep = nextStep;
                    steps++;
                    movie.setStatus(R.success());

//                    如果状态是确定的，可以持久化，那么改变movie 的step, 下次movie 可以从该状态继续
                    if( output.isPersistent() ) {
                        movie.setCurrent_step(currentStep);
                    }
                    this.mongoTemplate.save(movie);
                    // 如果不需要自动继续下一步，则退出循环
                    if( !autoNext || !context.autoNext() ) {
                        movie.getProcess_status().setProcessing(false);
                        this.mongoTemplate.save(movie);
                        return R.success(stepOutput);
                    }


                }
            } catch( Exception e ) {
                // 系统异常处理
                log.error("System error for movie [{}], current step: {}, nextStep: {}, error: {}",
                        movie.getId(), currentStep, nextStep, e.getMessage(), e);
                StepResult error = StepResult.error(e.getMessage());
                error.getData().put("currentStep", currentStep);
                saveError(movie, StepOutput.step(startTime, Optional.ofNullable(nextStep).orElse(currentStep), error));
                return R.fail(e.getMessage());
            }
        } finally {
            MDC.remove(INSTANCE_LOG_ID); // 正确清理 MDC
            context.complete();
        }
    }

    private <T extends Instance, K extends InstanceResult> StepResult handle(Context<T, K> context, TaskStep step) {
        context.setCurrentStep(step);
        String preResult = JsonUtil.toJson(context.getResult());
        T instance = context.getInstance();
        StepResult output = null;
        int max = Math.max(2,this.getMaxErrorCount());
        for( int errorCount = 0; errorCount < max; errorCount++ ) {
            try {
                if( Thread.currentThread().isInterrupted() ) {
                    throw new FatalException("instance cancel " + instance.getId());
                }
                output = this.movieDispatcher.handle(context, step.getKey());
                String currentResult = JsonUtil.toJson(context.getResult());
                if( !Objects.equals(preResult, currentResult) ) {
                    this.mongoTemplate.save(context.getResult());
                }
            } catch( Exception e ) {
                log.error("error for instance [{}], file name: {} , current step: {},  error: {}",
                        instance.getId(), instance.getName(), step, e.getMessage(), e);
                output = StepResult.error(e);
            }
            if( output.isSuccess() || !output.isRetryable() ) {
                break;
            }

        }


        return output;
    }

    private <T extends Instance> void saveError(T movie, StepOutput stepOutput) {
        movie.addStep(stepOutput);
        StepResult result = stepOutput.getResult();
        movie.setStatus(R.fail(result.getMessage()));
        Movie.ErrorStatus errorStatus = Optional.ofNullable(movie.getError()).orElse(new Movie.ErrorStatus());
        errorStatus.setError_count(errorStatus.getError_count() + 1);
        if( errorStatus.getError_count() > maxErrorCount || result.isFatal() ) {
            errorStatus.setPermanent(true);
        }
        movie.setError(errorStatus);
        movie.getProcess_status().setProcessing(false);
        this.mongoTemplate.save(movie);
    }


    @Override
    public void afterPropertiesSet() throws Exception {
//        PriorityBlockingQueue<Object> objects = new PriorityBlockingQueue<>();
//        objects.
        int maxPoolSize = this.getPoolSize();
        highExecutor = new ThreadPoolTaskExecutor();
        highExecutor.setThreadNamePrefix("movie-high-");
        highExecutor.setCorePoolSize(maxPoolSize);
        highExecutor.setMaxPoolSize(maxPoolSize);
        highExecutor.setQueueCapacity(100);
        highExecutor.setThreadPriority(Thread.MAX_PRIORITY);
        highExecutor.setRejectedExecutionHandler(new BlockPolicy());
        highExecutor.initialize();

        lowExecutor = new ThreadPoolTaskExecutor();
        lowExecutor.setThreadNamePrefix("movie-low-");
        lowExecutor.setCorePoolSize(maxPoolSize);
        lowExecutor.setMaxPoolSize(maxPoolSize);
        lowExecutor.setQueueCapacity(300);
        lowExecutor.setThreadPriority(Thread.NORM_PRIORITY);
        lowExecutor.setRejectedExecutionHandler(new BlockPolicy());
//        lowExecutor.setMaxPoolSize(this.maxPoolSize);
        lowExecutor.initialize();
        asyncTAskExecutor = new ThreadPoolTaskExecutor();
        asyncTAskExecutor.setThreadNamePrefix("movie-async-");
        asyncTAskExecutor.setCorePoolSize(maxPoolSize / 2);
        asyncTAskExecutor.setMaxPoolSize(maxPoolSize / 2);
        asyncTAskExecutor.setRejectedExecutionHandler(new BlockPolicy());
//        asyncTAskExecutor.setQueueCapacity(300);
        asyncTAskExecutor.setThreadPriority(Thread.NORM_PRIORITY);
//        lowExecutor.setMaxPoolSize(this.maxPoolSize);
        asyncTAskExecutor.initialize();
    }

    public int getPoolSize() {
        return 100;
    }

    public int getIdleCount() {
        return this.highExecutor.getQueueCapacity() - this.highExecutor.getQueueSize();
    }


    public class MovieWorker<T extends Instance, K extends InstanceResult> implements Runnable
    {
        private final Context<T, K> movieContext;
        private final T instance;

        public MovieWorker(Context<T, K> movieContext) {
            this.movieContext = movieContext;
            this.instance = movieContext.getInstance();
        }

        @Override
        public void run() {
            next(movieContext);
        }
    }

    @Override
    public void onApplicationEvent(TaskStatusEvent event) {
        TaskStatus status = event.getStatus();
        String taskId = event.getId();
        if( status != TaskStatus.running ) {
            cancelTask(taskId);
        }
    }

    private void cancelTask(String taskId) {
        Set<MovieWorker> workers = this.runningMovies.keySet().stream().filter(k -> k.instance.getTask_id().equals(taskId)).collect(Collectors.toSet());
        workers.forEach(worker -> {
            CompletableFuture<?> remove = this.runningMovies.remove(worker);
            try {
                remove.cancel(true);
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        });
        workers.forEach(worker -> {
            try {
                worker.instance.getProcess_status().setProcessing(false);
                this.mongoTemplate.save(worker.instance);
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        });
    }


}
