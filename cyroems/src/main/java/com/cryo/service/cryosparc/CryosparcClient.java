package com.cryo.service.cryosparc;

import com.alibaba.fastjson.JSONObject;
import com.cryo.task.export.cryosparc.CryosparcProject;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
@Slf4j
@RequiredArgsConstructor
public class CryosparcClient implements InitializingBean
{


    @Value("${app.cryosparc.maxJobs:100}")
    private int maxJobs = 100;
    private Semaphore jobLimiter;
    @Value("${app.cryosparc.baseUrl}")
    private String baseUrl;
    private RestClient restClient;
    @Value("${app.cryosparc.pollingInterval:60}")
    private long pollingInterval = 60;
    @Value("${app.cryosparc.healthUrl}")
    private String healthUrl;

    public JobResult submitJob(JobRequest jobRequest) {

        JobResult body = restClient.post().uri("/submit")
                .contentType(APPLICATION_JSON)
                .body(jobRequest).retrieve().body(JobResult.class);
        log.info("submit cryosparc job: {}, {}", JsonUtil.toJson(jobRequest), JsonUtil.toJson(body));
        return body;
    }

    public String findUser(String email, String password) {

        JsonNode body = restClient.post().uri("/user/get_id")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "email", email,
                        "password", password
                ))
                .retrieve().body(JsonNode.class);
        return body.path("user_id").asText();
    }

    public JobResult attach(String owner_user_id, String project_path) {
        return restClient.post().uri("/project/attach_for_user")
                .contentType(APPLICATION_JSON)
                .body(Map.of(

                        "target_user_id", owner_user_id,
                        "project_path", project_path
                )).retrieve().body(JobResult.class);
    }

    public JobResult detach(String projectUid) {
        return restClient.post().uri("/project/detach")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "project_uid", projectUid
                )).retrieve().body(JobResult.class);
    }


    public String createProject(String owner_user_id, String project_path, String project_title) {
        JsonNode body = restClient.post().uri("/project/create_by_path")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "owner_user_id", owner_user_id,
                        "project_path", project_path,
                        "project_title", project_title
                ))
                .retrieve().body(JsonNode.class);
        return body.path("project_uid").asText();
    }


    public CryosparcProject createWorkspace(String projectUid, String workspace_title, String workspace_desc) {
        JsonNode body = restClient.post().uri("/workspace/create")
                .contentType(APPLICATION_JSON)
                .body(Map.of(
                        "project_uid", projectUid,
                        "workspace_title", workspace_title,
                        "workspace_desc", workspace_desc
                ))
                .retrieve().body(JsonNode.class);
        CryosparcProject cryosparcProject = new CryosparcProject();
        cryosparcProject.setProject_uid(projectUid);
        cryosparcProject.setWorkspace_uid(body.path("workspace_uid").asText());
        cryosparcProject.setWorkspace_title(workspace_title);
        return cryosparcProject;
    }

    public JobState pollTaskState(String taskId) {

        return restClient.get().uri("/status/" + taskId).retrieve().body(JobState.class);
    }

    public JobState untilTaskComplete(String taskId, @Nullable Duration waitTime) {
        AtomicReference<JobState> at = new AtomicReference<>();
        waitTime = waitTime == null ? Duration.ofMinutes(120) : waitTime;
        Awaitility.waitAtMost(waitTime).pollInterval(Duration.ofSeconds(this.pollingInterval)).until(() -> {
            JobState jobState = pollTaskState(taskId);
            at.set(jobState);
            return jobState.isFinished() || jobState.getError() != null;
        });
        return at.get();
    }

    public CryosparcJob submitAndWait(JobRequest request) {
        Duration waitTime = Duration.ofMinutes(120);
        JobType jobType = request.getJob_type();
        switch( jobType ) {

            case import_particles, extract_micrographs_multi -> waitTime = Duration.ofMinutes(120);
            case class_2D_new -> waitTime = Duration.ofMinutes(180);
            case patch_motion_correction_multi, patch_ctf_estimation_multi, import_movies ->
                    waitTime = Duration.ofMinutes(600);

        }
        JobResult submitJob = null;
        JobState jobState = null;
        try {
            submitJob = this.submitJob(request);
            if( StringUtils.isBlank(submitJob.getTask_id()) ) {
                throw new RuntimeException("submit cryosparc job failed");
            }
            jobState = this.untilTaskComplete(submitJob.getTask_id(), waitTime);
            if( !jobState.isSuccess() ) {
                throw new RuntimeException(jobState.getError());
            }
        } catch( Exception e ) {
            log.error("patch cryosparc error", e);
            jobState = new JobState();
            jobState.setStatus("failed");
            jobState.setError(e.getMessage());
        }
        log.info("cryosparc job complete: {}, {}", JsonUtil.toJson(request), JsonUtil.toJson(jobState));
        return new CryosparcJob(request, submitJob, jobState);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        jobLimiter = new Semaphore(maxJobs);
        this.restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory())
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json").build();
    }

    /**
     * 测试与Cryosparc baseUrl的连接
     * 通过HEAD请求验证服务是否可达
     * @return 包含连接状态的结果对象
     */
    public JSONObject testBaseUrlConnection() {
        JSONObject result = new JSONObject();
        result.put("healthUrl", healthUrl);
        result.put("timestamp", LocalDateTime.now());

        try {
            log.info("Testing connection to Cryosparc baseUrl: {}", healthUrl);

            RestClient healthClient = RestClient.builder()
                    .requestFactory(new HttpComponentsClientHttpRequestFactory())
                    .baseUrl(healthUrl)
                    .defaultHeader("Content-Type", "application/json").build();
            JSONObject response = healthClient.get().uri("/health").retrieve().body(JSONObject.class);
            String remoteStatus = response == null ? null : response.getString("status");
            boolean connected = response != null;
            boolean healthy = connected && StringUtils.equalsIgnoreCase(remoteStatus, "ok");

            result.put("connected", connected);
            result.put("status", healthy ? "OK" : "DOWN");
            result.put("message", connected
                    ? response.getOrDefault("message", "Cryosparc health endpoint responded")
                    : "Cryosparc health endpoint returned an empty body");
            if (response != null) {
                result.put("remoteStatus", remoteStatus);
            }
        } catch (Exception e) {
            result.put("connected", false);
            result.put("status", "DOWN");
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            result.put("message", "Failed to connect to " + healthUrl);

            log.warn("Cryosparc connection test failed", e);
        }

        return result;
    }
}
