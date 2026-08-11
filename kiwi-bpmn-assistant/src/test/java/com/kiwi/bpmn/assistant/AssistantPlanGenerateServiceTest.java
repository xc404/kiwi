package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantPlanGenerateServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;
    @Mock
    ObjectProvider<ChatClient> chatClientProvider;

    private AssistantPlanGenerateService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AssistantRuleSet ruleSet = new AssistantRuleSet(objectMapper);
        ruleSet.init();
        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        service = new AssistantPlanGenerateService(
                objectMapper,
                chatClientProvider,
                ruleSet,
                new AssistantPlanCompiler(objectMapper),
                new AssistantBpmnToPlan(),
                true);
    }

    @Test
    void generate_createCompilesPlanIrAndInjectsRepairFeedback() {
        String response = """
                {
                  "summary":"已生成 HTTP 工作流",
                  "planIrJson":{
                    "processId":"http_flow",
                    "nodes":[
                      {"id":"StartEvent_1","type":"startEvent"},
                      {"id":"Activity_1","type":"serviceTask","componentId":"classpath_httpRequest",
                       "parameters":{"url":"${requestUrl}"}},
                      {"id":"EndEvent_1","type":"endEvent"}
                    ],
                    "flows":[
                      {"id":"Flow_1","sourceRef":"StartEvent_1","targetRef":"Activity_1"},
                      {"id":"Flow_2","sourceRef":"Activity_1","targetRef":"EndEvent_1"}
                    ]
                  },
                  "candidateXml":"<broken/>"
                }
                """;
        when(chatClient.prompt().user(org.mockito.ArgumentMatchers.anyString()).call().content())
                .thenReturn(response);
        clearInvocations(chatClient.prompt());
        String catalog = """
                {"installed":[{"id":"classpath_httpRequest","delegateExpression":"${httpRequest}",
                               "status":"installed"}],"installable":[],"templates":[]}
                """;

        var result = service.generate(
                "调用接口",
                catalog,
                "[{\"ruleId\":\"required_params_present\",\"message\":\"缺少 url\"}]",
                null,
                "URL 使用流程变量 requestUrl");

        assertTrue(result.getCandidateXml().contains("kiwi:componentId=\"classpath_httpRequest\""));
        assertTrue(result.getCandidateXml().contains("http://kiwi.com/bpmn"));
        assertFalse(result.getCandidateXml().contains("<broken/>"));
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient.prompt()).user(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("required_params_present"));
        assertTrue(prompt.contains("URL 使用流程变量 requestUrl"));
        assertTrue(prompt.contains("当前模式: create"));
        assertTrue(prompt.contains("禁止输出 candidateXml") || prompt.contains("planIrJson"));
        assertTrue(prompt.contains("不要判断或提议安装") || prompt.contains("提醒用户安装"));
    }

    @Test
    void generate_promptIncludesAllComponentsButOmitsInstallFlagsAndInstallIssues() {
        String response = """
                {
                  "summary":"ok",
                  "planIrJson":{
                    "processId":"http_flow",
                    "nodes":[
                      {"id":"StartEvent_1","type":"startEvent"},
                      {"id":"Activity_1","type":"serviceTask","componentId":"classpath_httpRequest",
                       "parameters":{"url":"https://example.com"}},
                      {"id":"EndEvent_1","type":"endEvent"}
                    ],
                    "flows":[
                      {"id":"Flow_1","sourceRef":"StartEvent_1","targetRef":"Activity_1"},
                      {"id":"Flow_2","sourceRef":"Activity_1","targetRef":"EndEvent_1"}
                    ]
                  }
                }
                """;
        when(chatClient.prompt().user(org.mockito.ArgumentMatchers.anyString()).call().content())
                .thenReturn(response);
        clearInvocations(chatClient.prompt());
        String catalog = """
                {"installed":[{"id":"classpath_httpRequest","delegateExpression":"${httpRequest}",
                               "status":"installed","requiresInstall":false}],
                 "installable":[{"id":"plugin_shell","name":"Shell","status":"available_to_install","requiresInstall":true}],
                 "templates":[]}
                """;
        String issues = """
                [{"code":"PLUGIN_NOT_INSTALLED","severity":"INSTALL","componentId":"plugin_shell","message":"需要安装"},
                 {"code":"MISSING_REQUIRED_PARAM","severity":"REPAIR","ruleId":"required_params_present","message":"缺少 url"}]
                """;

        service.generate("调用接口", catalog, issues, null, null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient.prompt()).user(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("classpath_httpRequest"));
        assertTrue(prompt.contains("plugin_shell"));
        assertTrue(prompt.contains("\"components\""));
        assertFalse(prompt.contains("requiresInstall"));
        assertFalse(prompt.contains("PLUGIN_NOT_INSTALLED"));
        assertTrue(prompt.contains("required_params_present"));
    }
}
