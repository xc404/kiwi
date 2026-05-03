package com.cryo.integration.workflow;

import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Movie 场景：组装变量、调用 {@link KiwiWorkflowClient}、回写 Mongo。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieKiwiWorkflowService {

    private final KiwiWorkflowClient kiwiWorkflowClient;
    private final KiwiWorkflowProperties properties;
    private final TaskRepository taskRepository;
    private final TaskDataSetRepository taskDataSetRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Movie 流水线是否可运行（客户端已配置且能解析出 BpmProcess.id：Task 优先，全局 movie-process-definition-id 为迁移回退）。
     */
    public boolean isMoviePipelineReady(Task task) {
        return kiwiWorkflowClient.isClientConfigured()
                && StringUtils.hasText(resolveMovieBpmProcessId(task));
    }

    /** 每轮调度最多处理的 movie 条数。 */
    public int getMovieBatchSize() {
        int n = properties.getMovieBatchSize();
        return n < 1 ? 1 : n;
    }

    /**
     * 若尚未写入 {@link com.cryo.model.Instance#getExternal_workflow_instance_id()} 则启动远程流程。
     * {@code taskDataset} 可为 null，此时会尝试根据 {@code task.taskSettings.dataset_id} 加载。
     */
    public void ensureStarted(Movie movie, Task task, TaskDataset taskDataset) throws Exception {
        String bpmId = resolveMovieBpmProcessId(task);
        if (!kiwiWorkflowClient.isClientConfigured() || !StringUtils.hasText(bpmId)) {
            return;
        }
        if (StringUtils.hasText(movie.getExternal_workflow_instance_id())) {
            return;
        }

        TaskDataset ds = taskDataset;
        if (ds == null && task.getTaskSettings() != null
                && StringUtils.hasText(task.getTaskSettings().getDataset_id())) {
            ds = taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElse(null);
        }

        Map<String, Object> vars = buildVariables(movie, task, ds);
        String instanceId = kiwiWorkflowClient.startProcess(bpmId, vars);
        movie.setExternal_workflow_instance_id(instanceId);
        mongoTemplate.save(movie);
        log.info("Started Kiwi workflow instance {} for movie {}", instanceId, movie.getId());
    }

    /**
     * 兼容旧调用路径：内部按 movie.task_id 再加载 Task。
     *
     * @deprecated 请改用 {@link #ensureStarted(Movie, Task, TaskDataset)}
     */
    @Deprecated
    public void ensureStarted(Movie movie) throws Exception {
        Task task = taskRepository.findById(movie.getTask_id()).orElseThrow();
        ensureStarted(movie, task, null);
    }

    private String resolveMovieBpmProcessId(Task task) {
        if (StringUtils.hasText(task.getMovieProcessDefinitionId())) {
            return task.getMovieProcessDefinitionId();
        }
        return properties.getMovieProcessDefinitionId();
    }

    private static Map<String, Object> buildVariables(Movie movie, Task task, TaskDataset taskDataset) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("movie", movie);
        vars.put("task", task);
        if (taskDataset != null) {
            vars.put("taskDataset", taskDataset);
        }
        return vars;
    }
}
