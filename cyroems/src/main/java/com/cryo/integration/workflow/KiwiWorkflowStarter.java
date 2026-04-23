package com.cryo.integration.workflow;

import com.cryo.dao.TaskRepository;
import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将单条 Movie 对应在 Kiwi-admin 侧启动 Camunda 流程实例；业务步骤仅在 Kiwi BPMN 中演进。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiwiWorkflowStarter {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final KiwiWorkflowProperties properties;
    private final TaskRepository taskRepository;
    private final MongoTemplate mongoTemplate;

    public boolean isEnabled() {
        return properties.isEnabled()
                && StringUtils.hasText(properties.getBaseUrl())
                && StringUtils.hasText(properties.getMovieProcessDefinitionId())
                && StringUtils.hasText(properties.getIntegrationSecret());
    }

    /** 每轮调度最多处理的 movie 条数（原依赖 {@code InstanceProcessor#getIdleCount}）。 */
    public int getMovieBatchSize() {
        int n = properties.getMovieBatchSize();
        return n < 1 ? 1 : n;
    }

    /**
     * 若尚未写入 {@link com.cryo.model.Instance#getExternal_workflow_instance_id()} 则启动远程流程。
     */
    public void ensureStarted(Movie movie) throws Exception {
        if (!isEnabled()) {
            return;
        }
        if (StringUtils.hasText(movie.getExternal_workflow_instance_id())) {
            return;
        }

        Task task = taskRepository.findById(movie.getTask_id()).orElseThrow();

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("movieId", movie.getId());
        vars.put("cryoTaskId", task.getId());

        Map<String, Object> body = Map.of("variables", vars);
        String json = JSON.writeValueAsString(body);

        String base = trimSlash(properties.getBaseUrl());
        URI uri = URI.create(base + "/bpm/integration/process/" + properties.getMovieProcessDefinitionId() + "/start");

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("X-Kiwi-Integration-Secret", properties.getIntegrationSecret())
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Kiwi start workflow HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = JSON.readTree(resp.body());
        String instanceId = root.path("id").asText(null);
        if (!StringUtils.hasText(instanceId)) {
            throw new IllegalStateException("Kiwi response missing process instance id: " + resp.body());
        }
        movie.setExternal_workflow_instance_id(instanceId);
        mongoTemplate.save(movie);
        log.info("Started Kiwi workflow instance {} for movie {}", instanceId, movie.getId());
    }

    private static String trimSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
