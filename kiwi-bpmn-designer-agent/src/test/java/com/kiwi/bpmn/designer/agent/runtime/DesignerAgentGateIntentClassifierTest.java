package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignerAgentGateIntentClassifierTest {

    private final DesignerAgentGateIntentClassifier classifier =
            new DesignerAgentGateIntentClassifier(new ObjectMapper(), stubChatProvider(), false);

    private static ObjectProvider<org.springframework.ai.chat.client.ChatClient> stubChatProvider() {
        return new ObjectProvider<>() {
            @Override
            public org.springframework.ai.chat.client.ChatClient getObject() {
                return null;
            }

            @Override
            public org.springframework.ai.chat.client.ChatClient getObject(Object... args) {
                return null;
            }

            @Override
            public org.springframework.ai.chat.client.ChatClient getIfAvailable() {
                return null;
            }

            @Override
            public org.springframework.ai.chat.client.ChatClient getIfUnique() {
                return null;
            }
        };
    }

    @Test
    void parseModelJson_accept() {
        var d = classifier.parseModelJson("{\"decision\":\"accept\"}", "好的");
        assertTrue(d.ok());
        assertTrue(d.accepted());
    }

    @Test
    void parseModelJson_rejectWithFeedback() {
        var d = classifier.parseModelJson(
                "```json\n{\"decision\":\"reject\",\"feedback\":\"去掉经理审批\"}\n```", "不要这样");
        assertTrue(d.ok());
        assertFalse(d.accepted());
        assertTrue(d.feedback().contains("经理"));
    }

    @Test
    void parseModelJson_rejectUsesUserMessageWhenFeedbackMissing() {
        var d = classifier.parseModelJson("{\"decision\":\"reject\"}", "拒绝，改并行网关");
        assertTrue(d.ok());
        assertFalse(d.accepted());
        assertTrue(d.feedback().contains("并行"));
    }
}
