package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.assistant.AssistantKeywordExtractor;
import com.kiwi.bpmn.assistant.AssistantPlanCompiler;
import com.kiwi.bpmn.assistant.AssistantPlanGenerateService;
import com.kiwi.bpmn.assistant.AssistantProperties;
import com.kiwi.bpmn.assistant.AssistantRuleSet;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.assistant.DefaultAssistantXmlValidator;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场景：用户输入「我想生成一个生成订单的流程」。
 * 默认走真实 LLM（读取环境变量或本地 gitignored yml 中的 DeepSeek Key）；无 Key 时自动跳过。
 */
@ExtendWith(MockitoExtension.class)
class AssistantCreateOrderScenarioTest {

    private static final String Scenario = "我想生成一个生成订单的流程";
    private static final Pattern ComponentIdPattern =
            Pattern.compile("kiwi:componentId=\"([^\"]+)\"");

    @Mock
    AssistantComponentLookup componentLookup;

    private ObjectMapper objectMapper;
    private AssistantRuleSet ruleSet;
    private AssistantKeywordExtractor keywordExtractor;
    private AssistantPlanGenerateService planGenerateService;
    private AssistantWorkflowValidator validator;
    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ruleSet = new AssistantRuleSet(objectMapper);
        ruleSet.init();

        Optional<String> apiKey = resolveDeepSeekApiKey();
        assumeTrue(apiKey.isPresent() && StringUtils.isNotBlank(apiKey.get()),
                "未找到 DEEPSEEK_API_KEY / KIWI_AI_API_KEY（环境变量或 application-local*.yml），跳过真实 LLM 场景");

        DeepSeekApi deepSeekApi = DeepSeekApi.builder().apiKey(apiKey.orElseThrow()).build();
        chatModel = DeepSeekChatModel.builder().deepSeekApi(deepSeekApi).build();
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        keywordExtractor = new AssistantKeywordExtractor(objectMapper, chatModelProvider);

        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClient> chatClientProvider = mock(ObjectProvider.class);
        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        planGenerateService = new AssistantPlanGenerateService(
                objectMapper,
                chatClientProvider,
                ruleSet,
                new AssistantPlanCompiler(objectMapper),
                new AssistantBpmnToPlan(),
                true);

        when(componentLookup.exists(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return id != null && id.startsWith("classpath_");
        });
        when(componentLookup.requiredInputKeys(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if ("classpath_httpRequest".equals(id)) {
                return List.of("url");
            }
            return List.of();
        });
        when(componentLookup.pluginMissingHint(anyString())).thenReturn(Optional.empty());
        when(componentLookup.resolveDelegateExpression(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            if (id != null && id.startsWith("classpath_")) {
                String bean = id.substring("classpath_".length());
                return Optional.of("${" + bean + "}");
            }
            return Optional.empty();
        });

        validator = new AssistantWorkflowValidator(
                new DefaultAssistantXmlValidator(),
                componentLookup,
                new AssistantProperties(),
                objectMapper,
                ruleSet);
    }

    @Test
    @Tag("llm")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void createOrderScenario_realLlm_extractsKeywordsAndValidates() throws Exception {
        long startedAt = System.currentTimeMillis();
        List<String> keywords = keywordExtractor.extract(Scenario);
        assertTrue(keywords.contains("订单"), "应抽到业务词「订单」: " + keywords);
        assertFalse(keywords.contains("生成"), "泛词「生成」应被过滤");
        assertFalse(keywords.contains("流程"), "泛词「流程」应被过滤");

        List<String> expectedIds = List.of(
                "classpath_uuidGenerate", "classpath_assignmentActivity", "classpath_httpRequest");
        var generated = planGenerateService.generate(Scenario, null, null, null);
        assertTrue(StringUtils.isNotBlank(generated.getCandidateXml()), "真实 LLM 应产出候选 BPMN");

        var validation = validator.validate(generated.getCandidateXml());
        boolean repaired = false;
        if (!AssistantVariables.DispatchPass.equals(validation.getDispatchCode())) {
            repaired = true;
            String issuesJson = objectMapper.writeValueAsString(validation.getIssues());
            generated = planGenerateService.generate(
                    Scenario,
                    issuesJson,
                    generated.getCandidateXml(),
                    "请按已装组件生成可执行创建订单流程：先 uuidGenerate 生成订单号，再 assignmentActivity 组装订单变量");
            validation = validator.validate(generated.getCandidateXml());
        }

        String xml = generated.getCandidateXml();
        assertTrue(xml.contains("<bpmn:startEvent") || xml.contains("<startEvent"), "应含开始事件");
        assertTrue(xml.contains("<bpmn:endEvent") || xml.contains("<endEvent"), "应含结束事件");
        assertTrue(xml.contains("<bpmndi:BPMNDiagram") || xml.contains("BPMNDiagram"), "应含图面");

        List<String> usedIds = extractComponentIds(xml);
        assertFalse(usedIds.isEmpty(), "创建订单流程应至少引用一个已装组件");
        for (String usedId : usedIds) {
            assertTrue(
                    expectedIds.contains(usedId),
                    "组件必须来自已装集合，禁止臆造: " + usedId);
        }
        assertTrue(
                usedIds.contains("classpath_uuidGenerate")
                        || usedIds.contains("classpath_assignmentActivity")
                        || StringUtils.containsIgnoreCase(generated.getAssistantReply(), "订单")
                        || StringUtils.containsIgnoreCase(xml, "订单"),
                "结果应与「订单」场景相关");

        assertEquals(
                AssistantVariables.DispatchPass,
                validation.getDispatchCode(),
                "真实 LLM 生成/一轮修复后应 PASS: " + validation.getIssues());
        assertTrue(validation.getIssues().isEmpty(), "PASS 时不应残留 issues: " + validation.getIssues());

        writeEvalReport(keywords, expectedIds, generated, usedIds, repaired, validation,
                System.currentTimeMillis() - startedAt);
    }

    private void writeEvalReport(
            List<String> keywords,
            List<String> catalogOrder,
            AssistantPlanGenerateService.GenerateResult generated,
            List<String> usedIds,
            boolean repaired,
            AssistantWorkflowValidator.ValidationResult validation,
            long elapsedMs) throws Exception {
        Path dir = Path.of("target", "ai-authoring-eval");
        Files.createDirectories(dir);
        Path report = dir.resolve("create-order-scenario.md");
        Path xmlFile = dir.resolve("create-order-candidate.bpmn.xml");
        Files.writeString(xmlFile, generated.getCandidateXml() == null ? "" : generated.getCandidateXml());

        boolean hasUuid = usedIds.contains("classpath_uuidGenerate");
        boolean hasAssign = usedIds.contains("classpath_assignmentActivity");
        boolean likePayCreate = hasUuid && hasAssign;
        boolean hasAssignmentsParam = StringUtils.contains(
                generated.getCandidateXml(), "name=\"assignments\"");
        boolean mentionsOrder = StringUtils.containsIgnoreCase(generated.getAssistantReply(), "订单")
                || StringUtils.containsIgnoreCase(generated.getCandidateXml(), "订单");

        StringBuilder md = new StringBuilder();
        md.append("# 创建订单场景真实 LLM 评测\n\n");
        md.append("- 场景: `").append(Scenario).append("`\n");
        md.append("- 耗时: ").append(elapsedMs).append(" ms\n");
        md.append("- 抽词: ").append(keywords).append('\n');
        md.append("- Catalog 排序: ").append(catalogOrder).append('\n');
        md.append("- 助手摘要: ").append(StringUtils.defaultString(generated.getAssistantReply())).append('\n');
        md.append("- PlanIR 非空: ").append(StringUtils.isNotBlank(generated.getPlanIrJson())).append('\n');
        md.append("- 使用组件: ").append(usedIds).append('\n');
        md.append("- 是否触发修复轮: ").append(repaired).append('\n');
        md.append("- 最终 dispatch: ").append(validation.getDispatchCode()).append('\n');
        md.append("- issues: ").append(validation.getIssues()).append('\n');
        md.append("- 贴近 pay-create（uuid→assign）: ").append(likePayCreate).append('\n');
        md.append("- 含 assignments 参数: ").append(hasAssignmentsParam).append('\n');
        md.append("- 文案/XML 含「订单」: ").append(mentionsOrder).append('\n');
        md.append("- 候选 XML: `").append(xmlFile.toAbsolutePath()).append("`\n");
        Files.writeString(report, md.toString());
        System.out.println(md);
    }

    private List<String> extractComponentIds(String xml) {
        Matcher matcher = ComponentIdPattern.matcher(xml);
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return List.copyOf(ids);
    }

    /**
     * 解析顺序：环境变量 → 系统属性 → 工作区 gitignored 的 application-local*.yml。
     * 不打印密钥内容。
     */
    private Optional<String> resolveDeepSeekApiKey() {
        for (String name : List.of("KIWI_AI_API_KEY", "DEEPSEEK_API_KEY")) {
            String fromEnv = StringUtils.trimToNull(System.getenv(name));
            if (fromEnv != null) {
                return Optional.of(fromEnv);
            }
            String fromProp = StringUtils.trimToNull(System.getProperty(name));
            if (fromProp != null) {
                return Optional.of(fromProp);
            }
        }
        Path backend = Path.of("").toAbsolutePath();
        List<Path> candidates = List.of(
                backend.resolve("src/main/resources/application-local.yml"),
                backend.resolve("src/main/resources/application-local2.yml"),
                backend.resolve("kiwi-admin/backend/src/main/resources/application-local.yml"),
                backend.resolve("kiwi-admin/backend/src/main/resources/application-local2.yml"));
        for (Path path : candidates) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("#") || !trimmed.contains(":")) {
                        continue;
                    }
                    int colon = trimmed.indexOf(':');
                    String key = trimmed.substring(0, colon).trim();
                    String value = trimmed.substring(colon + 1).trim();
                    if (("KIWI_AI_API_KEY".equals(key) || "DEEPSEEK_API_KEY".equals(key))
                            && StringUtils.isNotBlank(value)) {
                        return Optional.of(value.replaceAll("^['\"]|['\"]$", ""));
                    }
                }
            } catch (Exception ignored) {
                // 继续尝试下一个候选文件
            }
        }
        return Optional.empty();
    }
}
