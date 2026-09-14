package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignerAgentClarificationMergerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void merge_includesAnswersAndSupplement() throws Exception {
        String formJson =
                """
                {"questions":[{"id":"1","prompt":"Q?","options":[{"id":"A","label":"Opt A"}]}]}
                """;
        var result = DesignerAgentClarificationMerger.merge(
                objectMapper,
                "加审批",
                formJson,
                Map.of("1", "A"),
                List.of(),
                "驳回回到发起人");
        assertTrue(result.augmentedScenario().contains("加审批"));
        assertTrue(result.humanSummary().contains("Opt A"));
        assertTrue(result.humanSummary().contains("驳回回到发起人"));
        assertTrue(result.contextJson().contains("\"answers\""));
    }
}
