package com.kiwi.bpmn.designer.agent.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignerAgentPlanGeneratorParseTest {

    @Test
    void extractJsonPayload_stripsLeadingProse() {
        String raw = "I'll create a plan for you.\n{\"summary\":\"ok\",\"editPlan\":{\"operations\":[]}}";
        String json = DesignerAgentPlanGenerator.extractJsonPayload(raw);
        assertTrue(json.startsWith("{"));
        assertEquals("{\"summary\":\"ok\",\"editPlan\":{\"operations\":[]}}", json);
    }

    @Test
    void extractJsonPayload_stripsMarkdownFence() {
        String raw = """
                ```json
                {"summary":"x"}
                ```
                """;
        assertEquals("{\"summary\":\"x\"}", DesignerAgentPlanGenerator.extractJsonPayload(raw));
    }

    @Test
    void extractJsonPayload_unquotedKeys_canBeParsedLeniently() throws Exception {
        String raw = "{summary:\"写文件\",editPlan:{operations:[]}}";
        String json = DesignerAgentPlanGenerator.extractJsonPayload(raw);
        var mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .build();
        assertEquals("写文件", mapper.readTree(json).path("summary").asText());
    }
}
