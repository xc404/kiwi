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
}
