package com.kiwi.project.bpm.designer.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Designer Agent HTTP + SSE 评测客户端。
 */
public class DesignerAgentEvalClient {

    public record RunSnapshot(
            String runId,
            String targetProcessId,
            String stage,
            String candidateXml,
            String editPlanJson,
            String assistantReply,
            String issuesJson,
            String errorMessage,
            Boolean planSkipped,
            String baseBpmnXml,
            int repairRoundsObserved,
            List<String> sseEventTypes,
            long elapsedMs) {
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isReachable(String baseUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/signin"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(3))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception ignored) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/signin"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(3))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"userName\":\"__probe__\",\"password\":\"x\"}"))
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                return res.statusCode() > 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public String signIn(String baseUrl, String username, String password) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("userName", username)
                .put("password", password)
                .toString();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/auth/signin"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("登录 HTTP 失败: " + res.statusCode() + " " + res.body());
        }
        JsonNode root = objectMapper.readTree(res.body());
        JsonNode data = root.has("data") ? root.get("data") : root;
        String token = data.path("token").asText(null);
        if (StringUtils.isBlank(token)) {
            throw new IllegalStateException("登录响应无 token: " + res.body());
        }
        return token;
    }

    public RunSnapshot runCase(String baseUrl, String token, DesignerAgentEvalCase evalCase, String targetProcessId)
            throws Exception {
        long started = System.currentTimeMillis();
        SseSession session = new SseSession();
        String baseBpmnXml = evalCase.getBaseBpmnXml();

        JsonNode startBody = objectMapper.createObjectNode()
                .put("scenario", evalCase.getScenario())
                .put("targetProcessId", targetProcessId);
        if (StringUtils.isNotBlank(evalCase.getSelectedElementId())) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) startBody)
                    .put("selectedElementId", evalCase.getSelectedElementId());
        }
        if (StringUtils.isNotBlank(baseBpmnXml)) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) startBody).put("baseBpmnXml", baseBpmnXml);
        }

        consumeSse(
                baseUrl + "/bpm/designer-agent/runs/stream",
                token,
                startBody.toString(),
                session,
                s -> shouldPauseInitialStream(s),
                Duration.ofSeconds(180));

        String runId = session.runId;
        if (StringUtils.isBlank(runId)) {
            throw new IllegalStateException("SSE 未返回 runId");
        }

        JsonNode status = pollStatus(baseUrl, token, runId, Duration.ofSeconds(30));
        runId = firstNonBlank(text(status, "runId", null), session.runId);

        long deadline = System.currentTimeMillis() + Duration.ofSeconds(120).toMillis();
        while (System.currentTimeMillis() < deadline) {
            String stage = resolveStage(status, session);
            if (evalCase.isAutoConfirmPlan() && AgentRunStage.AwaitPlan.equals(stage)) {
                confirmPlan(baseUrl, token, runId, true, null);
                consumeSse(
                        baseUrl + "/bpm/designer-agent/runs/" + runId + "/stream/resume",
                        token,
                        null,
                        session,
                        s -> isStableStage(s.stage) || AgentRunStage.Error.equals(s.stage),
                        Duration.ofSeconds(180));
                status = pollStatus(baseUrl, token, runId, Duration.ofSeconds(30));
                continue;
            }
            if (isStableStage(stage) || AgentRunStage.Error.equals(stage)) {
                break;
            }
            Thread.sleep(2000);
            status = pollStatus(baseUrl, token, runId, Duration.ofSeconds(10));
        }

        return mergeSnapshot(runId, targetProcessId, status, session, baseBpmnXml, started);
    }

    private static String resolveStage(JsonNode status, SseSession session) {
        return firstNonBlank(text(status, "stage", null), session.stage);
    }

    private RunSnapshot mergeSnapshot(
            String runId,
            String targetProcessId,
            JsonNode status,
            SseSession session,
            String baseBpmnXml,
            long started) {
        return new RunSnapshot(
                firstNonBlank(text(status, "runId", null), runId),
                targetProcessId,
                firstNonBlank(text(status, "stage", null), session.stage),
                firstNonBlank(text(status, "candidateXml", null), session.candidateXml),
                firstNonBlank(text(status, "editPlanJson", null), session.editPlanJson),
                firstNonBlank(text(status, "assistantReply", null), session.assistantReply),
                firstNonBlank(text(status, "issuesJson", null), session.issuesJson),
                firstNonBlank(text(status, "errorMessage", null), session.errorMessage),
                status != null && status.has("planSkipped")
                        ? status.path("planSkipped").asBoolean()
                        : session.planSkipped,
                baseBpmnXml,
                session.repairRounds,
                List.copyOf(session.eventTypes),
                System.currentTimeMillis() - started);
    }

    private static boolean isStableStage(String stage) {
        return AgentRunStage.AwaitPreview.equals(stage)
                || AgentRunStage.AwaitAsk.equals(stage)
                || AgentRunStage.AwaitInstall.equals(stage)
                || AgentRunStage.Done.equals(stage)
                || AgentRunStage.Error.equals(stage);
    }

    private void confirmPlan(String baseUrl, String token, String runId, boolean confirmed, String editedPlanJson)
            throws Exception {
        var body = objectMapper.createObjectNode().put("confirmed", confirmed);
        if (StringUtils.isNotBlank(editedPlanJson)) {
            body.put("editedPlanJson", editedPlanJson);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/bpm/designer-agent/runs/" + runId + "/confirm-plan"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new IllegalStateException("confirm-plan 失败: " + res.statusCode() + " " + res.body());
            }
        } catch (java.net.http.HttpTimeoutException timeout) {
            // 服务端可能仍在异步 apply；后续轮询 status 即可
        }
    }

    private JsonNode pollStatus(String baseUrl, String token, String runId, Duration timeout) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/bpm/designer-agent/runs/" + runId))
                .header("Authorization", "Bearer " + token)
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 404) {
            return objectMapper.createObjectNode();
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("status 失败: " + res.statusCode() + " " + res.body());
        }
        JsonNode root = objectMapper.readTree(res.body());
        return root.has("data") ? root.get("data") : root;
    }

    private static boolean shouldPauseInitialStream(SseSession session) {
        if (StringUtils.isNotBlank(session.editPlanJson)
                && (session.eventTypes.contains("plan_ready") || AgentRunStage.AwaitPlan.equals(session.stage))) {
            return true;
        }
        if (StringUtils.isBlank(session.stage)) {
            return false;
        }
        return AgentRunStage.AwaitPlan.equals(session.stage) || isStableStage(session.stage);
    }

    private void consumeSse(
            String url,
            String token,
            String jsonBody,
            SseSession session,
            Predicate<SseSession> stopWhen,
            Duration maxWait) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "text/event-stream")
                .timeout(maxWait.plusSeconds(30));
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<InputStream> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream err = response.body()) {
                String msg = new String(err.readAllBytes(), StandardCharsets.UTF_8);
                if (msg.contains("Agent 未启用")) {
                    throw new AgentNotEnabledException(msg);
                }
                throw new IllegalStateException("SSE 失败: " + response.statusCode() + " " + msg);
            }
        }
        InputStream body = response.body();
        long deadline = System.currentTimeMillis() + maxWait.toMillis();
        Thread readerThread = Thread.startVirtualThread(() -> readSseStream(body, session));
        try {
            while (readerThread.isAlive() && System.currentTimeMillis() < deadline) {
                if (stopWhen.test(session)) {
                    body.close();
                    break;
                }
                Thread.sleep(200);
            }
            if (readerThread.isAlive()) {
                body.close();
            }
            readerThread.join(Duration.ofSeconds(5));
        } catch (InterruptedException e) {
            body.close();
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private void readSseStream(InputStream body, SseSession session) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            String eventName = "message";
            StringBuilder data = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (!data.isEmpty()) {
                        session.ingest(eventName, data.toString(), objectMapper);
                    }
                    eventName = "message";
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
            }
            if (!data.isEmpty()) {
                session.ingest(eventName, data.toString(), objectMapper);
            }
        } catch (Exception ignored) {
            // 流被主动关闭或服务器结束
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null || node.isMissingNode()) {
            return fallback;
        }
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return fallback;
        }
        String s = v.asText();
        return StringUtils.isBlank(s) ? fallback : s;
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.isNotBlank(a) ? a : b;
    }

    public static class AgentNotEnabledException extends RuntimeException {
        AgentNotEnabledException(String message) {
            super(message);
        }
    }

    private static final class SseSession {
        private String runId;
        private String stage;
        private String candidateXml;
        private String editPlanJson;
        private String assistantReply;
        private String issuesJson;
        private String errorMessage;
        private Boolean planSkipped;
        private int repairRounds;
        private final Set<String> eventTypes = new LinkedHashSet<>();

        private void ingest(String eventName, String data, ObjectMapper mapper) {
            eventTypes.add(StringUtils.defaultIfBlank(eventName, "message"));
            try {
                JsonNode node = mapper.readTree(data);
                if (node.has("type") && node.path("type").isTextual()) {
                    eventTypes.add(node.path("type").asText());
                }
                if (node.has("runId") && StringUtils.isNotBlank(node.path("runId").asText(null))) {
                    runId = node.path("runId").asText();
                }
                if (node.has("stage")) {
                    stage = node.path("stage").asText(null);
                    if (AgentRunStage.Repair.equals(stage)) {
                        repairRounds++;
                    }
                }
                if (node.has("candidateXml")) {
                    candidateXml = node.path("candidateXml").asText(null);
                }
                if (node.has("editPlanJson")) {
                    editPlanJson = node.path("editPlanJson").asText(null);
                }
                if (node.has("summary") && StringUtils.isBlank(assistantReply)) {
                    assistantReply = node.path("summary").asText(null);
                }
                if (node.has("assistantReply")) {
                    assistantReply = node.path("assistantReply").asText(null);
                }
                if (node.has("content") && StringUtils.isBlank(assistantReply)) {
                    assistantReply = node.path("content").asText(null);
                }
                if (node.has("issuesJson")) {
                    issuesJson = node.path("issuesJson").asText(null);
                }
                if (node.has("errorMessage")) {
                    errorMessage = node.path("errorMessage").asText(null);
                }
                if (node.has("planSkipped")) {
                    planSkipped = node.path("planSkipped").asBoolean();
                }
            } catch (Exception ignored) {
                // 非 JSON data 忽略
            }
        }
    }
}
