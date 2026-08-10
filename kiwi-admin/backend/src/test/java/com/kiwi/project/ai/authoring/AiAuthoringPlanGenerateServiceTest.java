package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AiChatProperties;
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
class AiAuthoringPlanGenerateServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    ChatClient chatClient;
    @Mock
    ObjectProvider<ChatClient> chatClientProvider;

    private AiAuthoringPlanGenerateService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        AiAuthoringRuleSet ruleSet = new AiAuthoringRuleSet(objectMapper);
        ruleSet.init();
        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        service = new AiAuthoringPlanGenerateService(
                objectMapper,
                new AiChatProperties(),
                chatClientProvider,
                ruleSet,
                new AiWorkflowPlanCompiler(objectMapper));
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
        assertFalse(result.getCandidateXml().contains("<broken/>"));
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClient.prompt()).user(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("required_params_present"));
        assertTrue(prompt.contains("URL 使用流程变量 requestUrl"));
        assertTrue(prompt.contains("当前模式: create"));
    }
}
