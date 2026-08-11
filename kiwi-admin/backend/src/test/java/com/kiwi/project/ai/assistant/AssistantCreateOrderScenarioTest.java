package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantKeywordExtractor;
import com.kiwi.bpmn.assistant.AssistantPlanCompiler;
import com.kiwi.bpmn.assistant.AssistantPlanGenerateService;
import com.kiwi.bpmn.assistant.AssistantProperties;
import com.kiwi.bpmn.assistant.AssistantRuleSet;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.assistant.DefaultAssistantXmlValidator;
import com.kiwi.bpmn.assistant.spi.AssistantBpmnLookup;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    BpmComponentService componentService;
    @Mock
    com.kiwi.project.bpm.dao.BpmComponentDao componentDao;
    @Mock
    AssistantBpmnLookup bpmnLookup;
    @Mock
    BpmComponentPluginLoader pluginLoader;
    @Mock
    AssistantComponentLookup componentLookup;
    @Mock
    ObjectProvider<BpmRemoteMarketService> remoteProvider;

    private ObjectMapper objectMapper;
    private AssistantRuleSet ruleSet;
    private AssistantKeywordExtractor keywordExtractor;
    private AssistantCatalogContextBuilder catalogBuilder;
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

        AssistantProperties properties = new AssistantProperties();
        properties.setEnabled(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        keywordExtractor = new AssistantKeywordExtractor(properties, objectMapper, chatModelProvider);

        when(pluginLoader.buildPluginJarIndex()).thenReturn(Map.of());
        when(remoteProvider.getIfAvailable()).thenReturn(null);
        when(bpmnLookup.findMatureTemplates(anyString(), any(), anyInt())).thenReturn(List.of());
        catalogBuilder = new AssistantCatalogContextBuilder(
                properties,
                componentService,
                componentDao,
                bpmnLookup,
                pluginLoader,
                objectMapper,
                remoteProvider);

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

        validator = new AssistantWorkflowValidator(
                new DefaultAssistantXmlValidator(),
                componentLookup,
                properties,
                objectMapper,
                ruleSet);
    }

    @Test
    @Tag("llm")
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void createOrderScenario_realLlm_extractsKeywordsRanksCatalogAndValidates() throws Exception {
        when(componentService.listCachedComponents()).thenReturn(List.of(
                component("classpath_httpRequest", "httpRequest", "HTTP 请求", "classpath", "通用"),
                component("classpath_uuidGenerate", "uuidGenerate", "生成 UUID / 订单号", "classpath", "通用"),
                component("classpath_assignmentActivity", "assignmentActivity", "组装订单变量", "classpath", "通用"),
                component("classpath_shell", "shell", "命令行", "classpath", "通用")));
        when(componentDao.findAll()).thenReturn(List.of(
                component("classpath_httpRequest", "httpRequest", "HTTP 请求", "classpath", "通用"),
                component("classpath_uuidGenerate", "uuidGenerate", "生成 UUID / 订单号", "classpath", "通用"),
                component("classpath_assignmentActivity", "assignmentActivity", "组装订单变量", "classpath", "通用"),
                component("classpath_shell", "shell", "命令行", "classpath", "通用")));
        when(componentService.fillComponentProperties(any(BpmComponent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        long startedAt = System.currentTimeMillis();
        List<String> keywords = keywordExtractor.extract(Scenario);
        assertTrue(keywords.contains("订单"), "应抽到业务词「订单」: " + keywords);
        assertFalse(keywords.contains("生成"), "泛词「生成」应被过滤");
        assertFalse(keywords.contains("流程"), "泛词「流程」应被过滤");

        AssistantCatalog catalog = catalogBuilder.build(Scenario, keywords);
        List<String> installedIds = catalog.getInstalled().stream()
                .map(AssistantCatalog.CatalogComponent::getId)
                .toList();
        assertTrue(installedIds.contains("classpath_uuidGenerate"));
        assertTrue(installedIds.contains("classpath_assignmentActivity"));

        String catalogJson = objectMapper.writeValueAsString(orderCatalog());
        var generated = planGenerateService.generate(Scenario, catalogJson, null, null, null);
        assertTrue(StringUtils.isNotBlank(generated.getCandidateXml()), "真实 LLM 应产出候选 BPMN");

        var validation = validator.validate(generated.getCandidateXml(), orderCatalog());
        boolean repaired = false;
        if (!AssistantVariables.DispatchPass.equals(validation.getDispatchCode())) {
            repaired = true;
            String issuesJson = objectMapper.writeValueAsString(validation.getIssues());
            generated = planGenerateService.generate(
                    Scenario,
                    catalogJson,
                    issuesJson,
                    generated.getCandidateXml(),
                    "请按 Catalog 已装组件生成可执行创建订单流程：先 uuidGenerate 生成订单号，再 assignmentActivity 组装订单变量");
            validation = validator.validate(generated.getCandidateXml(), orderCatalog());
        }

        String xml = generated.getCandidateXml();
        assertTrue(xml.contains("<bpmn:startEvent") || xml.contains("<startEvent"), "应含开始事件");
        assertTrue(xml.contains("<bpmn:endEvent") || xml.contains("<endEvent"), "应含结束事件");
        assertTrue(xml.contains("<bpmndi:BPMNDiagram") || xml.contains("BPMNDiagram"), "应含图面");

        List<String> usedIds = extractComponentIds(xml);
        assertFalse(usedIds.isEmpty(), "创建订单流程应至少引用一个 Catalog 组件");
        for (String usedId : usedIds) {
            assertTrue(
                    List.of("classpath_uuidGenerate", "classpath_assignmentActivity", "classpath_httpRequest")
                            .contains(usedId),
                    "组件必须来自 Catalog，禁止臆造: " + usedId);
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

        writeEvalReport(keywords, installedIds, generated, usedIds, repaired, validation,
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

    private AssistantCatalog orderCatalog() {
        AssistantCatalog catalog = new AssistantCatalog();
        AssistantCatalog.CatalogComponent uuid = new AssistantCatalog.CatalogComponent();
        uuid.setId("classpath_uuidGenerate");
        uuid.setName("生成 UUID");
        uuid.setDescription("生成订单号/UUID");
        uuid.setDelegateExpression("${uuidGenerate}");
        uuid.setStatus("installed");

        AssistantCatalog.CatalogComponent assign = new AssistantCatalog.CatalogComponent();
        assign.setId("classpath_assignmentActivity");
        assign.setName("变量组件");
        assign.setDescription("组装订单变量 outTradeNo / payAmount");
        assign.setDelegateExpression("${assignmentActivity}");
        assign.setStatus("installed");
        AssistantCatalog.CatalogParameter assignments = new AssistantCatalog.CatalogParameter();
        assignments.setKey("assignments");
        assignments.setRequired(false);
        assignments.setExample("[{\"key\":\"outTradeNo\",\"value\":\"${uuid}\"}]");
        assign.setInputs(List.of(assignments));

        AssistantCatalog.CatalogComponent http = new AssistantCatalog.CatalogComponent();
        http.setId("classpath_httpRequest");
        http.setName("HTTP 请求");
        http.setDelegateExpression("${httpRequest}");
        http.setStatus("installed");
        AssistantCatalog.CatalogParameter url = new AssistantCatalog.CatalogParameter();
        url.setKey("url");
        url.setRequired(true);
        http.setInputs(List.of(url));

        catalog.setInstalled(List.of(uuid, assign, http));
        return catalog;
    }

    private List<String> extractComponentIds(String xml) {
        Matcher matcher = ComponentIdPattern.matcher(xml);
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return List.copyOf(ids);
    }

    private BpmComponent component(String id, String key, String name, String source, String group) {
        BpmComponent component = new BpmComponent();
        component.setId(id);
        component.setKey(key);
        component.setName(name);
        component.setSource(source);
        component.setGroup(group);
        component.setDescription(name);
        return component;
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
