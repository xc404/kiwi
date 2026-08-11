package com.kiwi.project.ai.assistant;

import com.kiwi.bpmn.assistant.AssistantVariables;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 对着已启动的 backend 做「创建订单」场景 HTTP 接口评测。
 * <p>
 * 默认基址 {@code http://127.0.0.1:8080}，可用环境变量覆盖：
 * {@code KIWI_API_BASE_URL}、{@code KIWI_API_USERNAME}、{@code KIWI_API_PASSWORD}。
 * 服务未启动时自动跳过。
 */
class AssistantCreateOrderApiIT {

    private static final String Scenario = "我想生成一个生成订单的流程";
    private static final Pattern ComponentIdPattern =
            Pattern.compile("kiwi:componentId=\"([^\"]+)\"");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    @Tag("api")
    @Tag("llm")
    @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void createOrder_viaWorkflowAuthoringApi_evaluatesEffect() throws Exception {
        String baseUrl = StringUtils.removeEnd(
                StringUtils.defaultIfBlank(System.getenv("KIWI_API_BASE_URL"), "http://127.0.0.1:8080"),
                "/");
        assumeTrue(isReachable(baseUrl), "后端未启动，跳过接口测试: " + baseUrl);

        String username = StringUtils.defaultIfBlank(System.getenv("KIWI_API_USERNAME"), "admin");
        String password = StringUtils.defaultIfBlank(System.getenv("KIWI_API_PASSWORD"), "123456");

        long started = System.currentTimeMillis();
        String token = signIn(baseUrl, username, password);
        assertTrue(StringUtils.isNotBlank(token), "登录应返回 token");

        String targetProcessId = "api-eval-create-order-" + System.currentTimeMillis();
        JsonNode startData = startAuthoring(baseUrl, token, Scenario, targetProcessId);
        long elapsed = System.currentTimeMillis() - started;

        String stage = text(startData, "stage");
        String dispatch = text(startData, "dispatchCode");
        String reply = text(startData, "assistantReply");
        String xml = text(startData, "candidateXml");
        String issues = text(startData, "issuesJson");
        String catalog = text(startData, "catalogJson");
        JsonNode tasks = startData.path("tasks");

        assertNotNull(startData.path("processInstanceId").asText(null));
        assertTrue(StringUtils.isNotBlank(xml), "接口应返回 candidateXml");
        assertTrue(
                AssistantVariables.StageAwaitPreview.equals(stage)
                        || AssistantVariables.StageAwaitAsk.equals(stage)
                        || AssistantVariables.StageAwaitInstall.equals(stage)
                        || (tasks.isArray() && !tasks.isEmpty()),
                "应进入预览/追问/安装等人机阶段, stage=" + stage + ", tasks=" + tasks);

        List<String> usedIds = extractComponentIds(xml);
        boolean hasStart = xml.contains("startEvent") || xml.contains("StartEvent");
        boolean hasEnd = xml.contains("endEvent") || xml.contains("EndEvent");
        boolean hasDiagram = xml.contains("BPMNDiagram");
        boolean mentionsOrder = StringUtils.containsIgnoreCase(reply, "订单")
                || StringUtils.containsIgnoreCase(xml, "订单");
        boolean likePayCreate = usedIds.contains("classpath_uuidGenerate")
                && usedIds.contains("classpath_assignmentActivity");

        int catalogInstalled = 0;
        if (StringUtils.isNotBlank(catalog)) {
            JsonNode catalogNode = objectMapper.readTree(catalog);
            if (catalogNode.path("installed").isArray()) {
                catalogInstalled = catalogNode.path("installed").size();
            }
        }

        Path dir = Path.of("target", "ai-authoring-eval");
        Files.createDirectories(dir);
        Path xmlFile = dir.resolve("create-order-api-candidate.bpmn.xml");
        Path report = dir.resolve("create-order-api.md");
        Files.writeString(xmlFile, xml, StandardCharsets.UTF_8);

        int score = 0;
        if (hasStart && hasEnd) {
            score += 2;
        }
        if (hasDiagram) {
            score += 1;
        }
        if (catalogInstalled > 0) {
            score += 2;
        }
        if (!usedIds.isEmpty()) {
            score += 2;
        }
        if (likePayCreate) {
            score += 2;
        }
        if (mentionsOrder) {
            score += 1;
        }
        if (AssistantVariables.DispatchPass.equals(dispatch)
                || AssistantVariables.StageAwaitPreview.equals(stage)) {
            score += 1;
        }

        StringBuilder md = new StringBuilder();
        md.append("# 创建订单场景 HTTP 接口评测\n\n");
        md.append("- baseUrl: `").append(baseUrl).append("`\n");
        md.append("- 场景: `").append(Scenario).append("`\n");
        md.append("- targetProcessId: `").append(targetProcessId).append("`\n");
        md.append("- processInstanceId: `").append(text(startData, "processInstanceId")).append("`\n");
        md.append("- 耗时: ").append(elapsed).append(" ms\n");
        md.append("- stage: ").append(stage).append('\n');
        md.append("- dispatchCode: ").append(dispatch).append('\n');
        md.append("- active: ").append(startData.path("active").asBoolean()).append('\n');
        md.append("- tasks: ").append(tasks).append('\n');
        md.append("- 助手摘要: ").append(StringUtils.defaultString(reply)).append('\n');
        md.append("- issuesJson: ").append(StringUtils.defaultString(issues)).append('\n');
        md.append("- catalog.installed 数量: ").append(catalogInstalled).append('\n');
        md.append("- 使用组件: ").append(usedIds).append('\n');
        md.append("- 含 start/end/diagram: ").append(hasStart).append('/')
                .append(hasEnd).append('/').append(hasDiagram).append('\n');
        md.append("- 贴近 pay-create（uuid→assign）: ").append(likePayCreate).append('\n');
        md.append("- 文案/XML 含「订单」: ").append(mentionsOrder).append('\n');
        md.append("- 效果评分: ").append(score).append("/11\n");
        md.append("- 候选 XML: `").append(xmlFile.toAbsolutePath()).append("`\n");
        Files.writeString(report, md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);

        assertTrue(hasStart && hasEnd, "BPMN 应含开始与结束事件");
        assertTrue(
                AssistantVariables.StageAwaitPreview.equals(stage)
                        || AssistantVariables.StageAwaitAsk.equals(stage)
                        || AssistantVariables.StageAwaitInstall.equals(stage)
                        || (tasks.isArray() && !tasks.isEmpty()),
                "应进入人机确认阶段");
        assertTrue(catalogInstalled > 0,
                "Catalog.installed 不应为空（组件已在库中）；详见 " + report.toAbsolutePath());
        assertFalse(usedIds.isEmpty(), "应至少引用一个 Catalog 组件");
        assertTrue(score >= 7, "接口效果评分过低: " + score + "/11");
    }

    private boolean isReachable(String baseUrl) {
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
                // 任意 HTTP 响应都说明端口通了（401/业务失败均可）
                return res.statusCode() > 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private String signIn(String baseUrl, String username, String password) throws Exception {
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
        assertTrue(res.statusCode() >= 200 && res.statusCode() < 300,
                "登录 HTTP 失败: " + res.statusCode() + " " + res.body());
        JsonNode root = objectMapper.readTree(res.body());
        JsonNode data = root.has("data") ? root.get("data") : root;
        String token = data.path("token").asText(null);
        assertTrue(StringUtils.isNotBlank(token), "登录响应无 token: " + res.body());
        return token;
    }

    private JsonNode startAuthoring(
            String baseUrl, String token, String scenario, String targetProcessId) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("scenario", scenario)
                .put("targetProcessId", targetProcessId)
                .toString();
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/ai/workflow-authoring/start"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(200))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(res.statusCode() >= 200 && res.statusCode() < 300,
                "start 接口失败: " + res.statusCode() + " " + res.body());
        JsonNode root = objectMapper.readTree(res.body());
        if (root.has("success") && !root.path("success").asBoolean(true)) {
            throw new AssertionError("start 业务失败: " + res.body());
        }
        if (root.has("code") && root.path("code").asInt(0) != 0 && !root.path("success").asBoolean(false)) {
            // mica R: success=true / code=0 为成功；兼容仅有 data 的响应
            if (!root.has("data")) {
                throw new AssertionError("start 业务失败: " + res.body());
            }
        }
        JsonNode data = root.has("data") ? root.get("data") : root;
        assertFalse(data.isMissingNode() || data.isNull(), "start 响应缺少 data: " + res.body());
        return data;
    }

    private List<String> extractComponentIds(String xml) {
        Matcher matcher = ComponentIdPattern.matcher(xml == null ? "" : xml);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return StringUtils.isBlank(s) ? null : s;
    }
}
