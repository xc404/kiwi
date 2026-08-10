package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.system.ai.BpmDesignerXmlValidator;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 确定性质量门禁：规则、Catalog 或编译器迭代后，黄金场景必须全部继续通过。
 */
class AiWorkflowGoldenEvaluationTest {

    @Test
    void goldenCases_compileToExpectedValidBpmn() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        List<GoldenCase> cases;
        try (InputStream input = getClass().getResourceAsStream("/ai-authoring/golden-cases.json")) {
            assertTrue(input != null, "缺少黄金评测集");
            cases = objectMapper.readValue(input, new TypeReference<>() {
            });
        }
        AiWorkflowPlanCompiler compiler = new AiWorkflowPlanCompiler(objectMapper);
        BpmDesignerXmlValidator xmlValidator = new BpmDesignerXmlValidator();
        List<String> failures = new ArrayList<>();
        int passed = 0;

        for (GoldenCase goldenCase : cases) {
            try {
                String xml = compiler.compile(goldenCase.getPlan(), goldenCase.getCatalog());
                xmlValidator.validate(xml);
                for (String expected : goldenCase.getExpectedFragments()) {
                    if (!xml.contains(expected)) {
                        failures.add(goldenCase.getId() + ": 缺少片段 " + expected);
                    }
                }
                for (String forbidden : goldenCase.getForbiddenFragments()) {
                    if (xml.contains(forbidden)) {
                        failures.add(goldenCase.getId() + ": 出现禁止片段 " + forbidden);
                    }
                }
                if (failures.stream().noneMatch(f -> f.startsWith(goldenCase.getId() + ":"))) {
                    passed++;
                }
            } catch (Exception e) {
                failures.add(goldenCase.getId() + ": " + e.getMessage());
            }
        }

        assertEquals(cases.size(), passed,
                "黄金评测通过率 " + passed + "/" + cases.size() + "；失败：\n" + String.join("\n", failures));
    }

    @Data
    public static class GoldenCase {
        private String id;
        private AiAuthoringCatalog catalog;
        private AiWorkflowPlan plan;
        private List<String> expectedFragments = new ArrayList<>();
        private List<String> forbiddenFragments = new ArrayList<>();
    }
}
