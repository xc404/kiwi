package com.cryo.task.engine;


import com.cryo.common.mongo.MongoTemplate;
import com.cryo.model.Instance;
import com.cryo.model.InstanceResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.settings.TaskSettings;
import com.cryo.service.TaskSettingService;
import com.cryo.task.engine.flow.IFlow;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class BaseContext<T extends Instance, R extends InstanceResult> implements Context<T, R>
{
    protected final static ThreadLocal<BaseContext> threadLocal = new ThreadLocal<>();
    @Getter
    protected final TaskDataset taskDataset;
    @Getter
    protected final Task task;

    //    protected final InstanceResultRepository<R> instanceResultRepository;
    @Getter
    protected final T instance;
    protected final Map<String, Object> context = new HashMap<>();
    @Getter
    protected final IFlow<T, R> flow;
    protected final TaskSettings defaultTaskSettings;
    private final MongoTemplate mongoTemplate;
    private final Class<R> resultClass;
    @Getter
    @Setter
    protected TaskStep currentStep;
//    protected StringWriter slurmCmds = new StringWriter();

    public BaseContext(ApplicationContext applicationContext, TaskDataset taskDataset, IFlow<T, R> flow, Task task, T instance, Class<R> resultClass) {
        this.taskDataset = taskDataset;
        this.task = task;
        this.flow = flow;
        this.instance = instance;
//        this.applicationContext = applicationContext;
        this.mongoTemplate = applicationContext.getBean(MongoTemplate.class);
        TaskSettingService taskSettingService = applicationContext.getBean(TaskSettingService.class);
        this.defaultTaskSettings = taskSettingService.getDefaultTaskSettings(task);
        this.resultClass = resultClass;

    }

    public Object put(String key, Object value) {
        return context.put(key, value);
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return context.getOrDefault(key, defaultValue);
    }

    public Object get(String key) {
        return context.get(key);
    }

    public void start() {
        threadLocal.set(this);
    }

    public static Context get() {
        return threadLocal.get();
    }

    public void complete() {
        threadLocal.remove();
    }


    public String getContextDir() {
//        if( equalsDefault() ) {
//            return "default";
//        } else {
        return task.getWork_dir();
//        }
    }

//
//    public boolean equalsDefault() {
//        return task.equalsDefault();
//    }


    @NonNull
    public synchronized R getResult() {
        return getResult(getCurrentConfigId());

    }

    @NonNull
    private synchronized R getResult(String configId) {
        if( configId == null ) {
            throw new RuntimeException("configId is null, taskId: " + task.getId() + ", instanceId: " + instance.getId());
        }
        String key = "_instance_result_" + configId;
        R result = (R) this.context.get(key);
        if( result == null ) {
            Query query = Query.query(Criteria.where("data_id").is(instance.getData_id()).and("config_id").is(configId));
            result = this.mongoTemplate.findOne(query, resultClass);
            if( result == null ) {
                result = createResult();
                result.setTask_data_id(task.getTaskSettings().getDataset_id());
                result.setData_id(instance.getData_id());
                result.setConfig_id(configId);
                result.setInstance_id(instance.getId());
                result.setTask_id(task.getId());
                String category = "default";
                result.setCategory(category);
                this.mongoTemplate.save(result);
            }
            this.context.put(key, result);
        }
        return result;
    }

    protected R createResult() {
        try {
            return resultClass.getConstructor().newInstance();
        } catch( InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e ) {
            throw new RuntimeException(e);
        }
    }

    public R getDefaultResult() {
        return getResult(task.getDefault_config_id());
    }


    public boolean forceReset() {
        return instance.isForceReset();
    }


    public String getCurrentConfigId() {
        return this.getTask().getDefault_config_id();
    }

    public TaskSettings getTaskSettings() {
        return this.task.getTaskSettings();
    }
}
