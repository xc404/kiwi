package com.kiwi.project.bpm.designer.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.bpm.designer.agent.support.DesignerAgentEvalCase;
import com.kiwi.project.bpm.designer.agent.support.DesignerAgentEvalClient;
import com.kiwi.project.bpm.designer.agent.support.DesignerAgentEvalRunResult;
import com.kiwi.project.bpm.designer.agent.support.DesignerAgentEvalScorer;
import com.kiwi.project.bpm.designer.agent.support.DesignerAgentEvalTriage;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Designer Agent HTTP 集成评测：真实 LLM + SSE + 结构化打分与归因报告。
 * <p>
 * 默认场景「帮我做一个写文件的流程」，期望选用 {@code classpath_fileWrite} 组件。
 * <p>
 * 前置条件：
 * <ul>
 *   <li>backend 已启动（默认 {@code http://127.0.0.1:8080}，可用 {@code KIWI_API_BASE_URL} 覆盖）</li>
 *   <li>{@code KIWI_BPM_DESIGNER_AGENT_ENABLED=true}</li>
 *   <li>{@code DEEPSEEK_API_KEY} 或 {@code KIWI_AI_API_KEY} 已配置</li>
 * </ul>
 * <p>
 * 运行：{@code mvn -pl kiwi-admin/backend test -Dtest=DesignerAgentCapabilityEvalIT}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DesignerAgentCapabilityEvalIT {

    private static final Path OutputDir = Path.of("target", "designer-agent-eval");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DesignerAgentEvalClient client = new DesignerAgentEvalClient();
    private final DesignerAgentEvalScorer scorer = new DesignerAgentEvalScorer();
    private final DesignerAgentEvalTriage triage = new DesignerAgentEvalTriage();
    private final List<DesignerAgentEvalRunResult> allResults = new ArrayList<>();

    static Stream<DesignerAgentEvalCase> evalCases() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = DesignerAgentCapabilityEvalIT.class
                .getResourceAsStream("/ai-authoring/designer-agent-eval-cases.json")) {
            assumeTrue(input != null, "缺少 designer-agent-eval-cases.json");
            List<DesignerAgentEvalCase> cases = mapper.readValue(input, new TypeReference<>() {
            });
            return cases.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("evalCases")
    @Tag("api")
    @Tag("llm")
    @Tag("designer-agent")
    @Timeout(value = 600, unit = TimeUnit.SECONDS)
    void evaluateCapability(DesignerAgentEvalCase evalCase) throws Exception {
        String baseUrl = StringUtils.removeEnd(
                StringUtils.defaultIfBlank(System.getenv("KIWI_API_BASE_URL"), "http://127.0.0.1:8080"),
                "/");
        assumeTrue(client.isReachable(baseUrl), "后端未启动，跳过: " + baseUrl);

        String username = StringUtils.defaultIfBlank(System.getenv("KIWI_API_USERNAME"), "admin");
        String password = StringUtils.defaultIfBlank(System.getenv("KIWI_API_PASSWORD"), "123456");

        String token;
        try {
            token = client.signIn(baseUrl, username, password);
        } catch (Exception e) {
            assumeTrue(false, "登录失败: " + e.getMessage());
            return;
        }

        String targetProcessId = "eval-" + evalCase.getId() + "-" + System.currentTimeMillis();
        DesignerAgentEvalClient.RunSnapshot snapshot;
        try {
            snapshot = client.runCase(baseUrl, token, evalCase, targetProcessId);
        } catch (DesignerAgentEvalClient.AgentNotEnabledException e) {
            assumeTrue(false, "BPM 设计器 Agent 未启用，请设置 KIWI_BPM_DESIGNER_AGENT_ENABLED=true");
            return;
        }

        DesignerAgentEvalRunResult result = scorer.score(evalCase, snapshot);
        allResults.add(result);
        writeCaseArtifacts(evalCase, result);

        assertTrue(
                result.isPassed(),
                () -> result.getCaseId() + " 评测未通过 score=" + result.getScore() + "/" + result.getMaxScore()
                        + " failures=" + result.getFailureReasons()
                        + " 详见 " + OutputDir.toAbsolutePath());
    }

    @org.junit.jupiter.api.AfterAll
    void writeSummaryAndTriage() throws Exception {
        if (allResults.isEmpty()) {
            return;
        }
        Files.createDirectories(OutputDir);
        DesignerAgentEvalTriage.Baseline baseline = loadBaseline();

        Map<String, Object> summary = new LinkedHashMap<>();
        long passed = allResults.stream().filter(DesignerAgentEvalRunResult::isPassed).count();
        summary.put("passed", passed);
        summary.put("total", allResults.size());
        summary.put("passRate", allResults.isEmpty() ? 0 : (double) passed / allResults.size());
        summary.put("results", allResults);

        Path summaryJson = OutputDir.resolve("summary.json");
        Files.writeString(summaryJson, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));

        String triageMd = triage.buildTriageReport(allResults, baseline);
        Files.writeString(OutputDir.resolve("triage.md"), triageMd, StandardCharsets.UTF_8);

        StringBuilder summaryMd = new StringBuilder();
        summaryMd.append("# Designer Agent 能力评测汇总\n\n");
        summaryMd.append("- 通过: ").append(passed).append('/').append(allResults.size()).append('\n');
        for (DesignerAgentEvalRunResult r : allResults) {
            summaryMd.append("- **").append(r.getCaseId()).append("**: ")
                    .append(r.isPassed() ? "PASS" : "FAIL")
                    .append(" score=").append(r.getScore()).append('/').append(r.getMaxScore())
                    .append(" stage=").append(r.getStage())
                    .append(" components=").append(r.getComponentIdsUsed())
                    .append('\n');
        }
        summaryMd.append("\n详见 `").append(summaryJson.toAbsolutePath()).append("`\n");
        Files.writeString(OutputDir.resolve("summary.md"), summaryMd.toString(), StandardCharsets.UTF_8);
        System.out.println(summaryMd);

        if (baseline != null && "true".equalsIgnoreCase(System.getenv("KIWI_EVAL_UPDATE_BASELINE"))) {
            updateBaseline(allResults, baseline);
        }
    }

    private void writeCaseArtifacts(DesignerAgentEvalCase evalCase, DesignerAgentEvalRunResult result) throws Exception {
        Files.createDirectories(OutputDir);
        if (StringUtils.isNotBlank(result.getCandidateXml())) {
            Files.writeString(OutputDir.resolve(evalCase.getId() + ".bpmn.xml"), result.getCandidateXml());
        }
        if (StringUtils.isNotBlank(result.getEditPlanJson())) {
            Files.writeString(OutputDir.resolve(evalCase.getId() + ".edit-plan.json"), result.getEditPlanJson());
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(evalCase.getId()).append(" 评测\n\n");
        md.append("- 场景: `").append(evalCase.getScenario()).append("`\n");
        md.append("- 耗时: ").append(result.getElapsedMs()).append(" ms\n");
        md.append("- 得分: ").append(result.getScore()).append('/').append(result.getMaxScore()).append('\n');
        md.append("- stage: ").append(result.getStage()).append('\n');
        md.append("- planSkipped: ").append(result.getPlanSkipped()).append('\n');
        md.append("- repairRounds: ").append(result.getRepairRoundsObserved()).append('\n');
        md.append("- 组件: ").append(result.getComponentIdsUsed()).append('\n');
        md.append("- 摘要: ").append(StringUtils.defaultString(result.getAssistantReply())).append('\n');
        md.append("- issues: ").append(StringUtils.defaultString(result.getIssuesJson())).append('\n');
        md.append("- 失败原因: ").append(result.getFailureReasons()).append('\n');
        md.append("- 分项: ").append(result.getScoreBreakdown()).append('\n');
        Files.writeString(OutputDir.resolve(evalCase.getId() + ".md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);
    }

    private DesignerAgentEvalTriage.Baseline loadBaseline() {
        try (InputStream input = getClass().getResourceAsStream("/ai-authoring/designer-agent-eval-baseline.json")) {
            if (input == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(input);
            Map<String, DesignerAgentEvalTriage.CaseBaseline> cases = new LinkedHashMap<>();
            JsonNode caseNode = root.path("cases");
            caseNode.fieldNames().forEachRemaining(id -> cases.put(
                    id,
                    new DesignerAgentEvalTriage.CaseBaseline(caseNode.path(id).path("minScore").asInt(0))));
            return new DesignerAgentEvalTriage.Baseline(
                    root.path("version").asInt(1),
                    root.path("minPassRate").asDouble(1.0),
                    cases);
        } catch (Exception e) {
            return null;
        }
    }

    private void updateBaseline(List<DesignerAgentEvalRunResult> results, DesignerAgentEvalTriage.Baseline baseline)
            throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", baseline.version());
        root.put("minPassRate", baseline.minPassRate());
        Map<String, Object> cases = new LinkedHashMap<>();
        for (DesignerAgentEvalRunResult r : results) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("minScore", r.getScore());
            cases.put(r.getCaseId(), c);
        }
        root.put("cases", cases);
        Path baselinePath = Path.of("src/test/resources/ai-authoring/designer-agent-eval-baseline.json");
        Files.writeString(baselinePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        System.out.println("已更新 baseline: " + baselinePath.toAbsolutePath());
    }
}
