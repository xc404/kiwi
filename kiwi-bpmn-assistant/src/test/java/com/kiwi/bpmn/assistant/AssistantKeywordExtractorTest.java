package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantKeywordExtractorTest {

    @Test
    void extract_expandsHttpSynonyms() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        AssistantKeywordExtractor extractor =
                new AssistantKeywordExtractor(new AssistantProperties(), new ObjectMapper(), provider);
        List<String> kws = extractor.extract("帮我做一个调用 HTTP 接口通知的场景流程");
        assertTrue(kws.stream().anyMatch(k -> k.contains("http")));
        assertFalse(kws.isEmpty());
    }

    @Test
    void extract_mergesShortLlmKeywordsAndRules() {
        AssistantProperties properties = new AssistantProperties();
        properties.setEnabled(true);
        ChatModel chatModel = mock(ChatModel.class);
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage output = mock(AssistantMessage.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);

        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);
        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(output);
        when(output.getText()).thenReturn("""
                ```json
                {"keywords":["订单","Webhook"],"tags":["通知","工作流"]}
                ```
                """);

        AssistantKeywordExtractor extractor =
                new AssistantKeywordExtractor(properties, new ObjectMapper(), provider);

        List<String> keywords = extractor.extract("订单完成后调用 webhook 发送通知");

        assertTrue(keywords.contains("订单"));
        assertTrue(keywords.contains("webhook"));
        assertTrue(keywords.contains("notify"));
        assertFalse(keywords.contains("工作流"));
    }
}
