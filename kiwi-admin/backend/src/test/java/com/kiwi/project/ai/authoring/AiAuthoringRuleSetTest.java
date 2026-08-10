package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAuthoringRuleSetTest {

    private AiAuthoringRuleSet ruleSet;

    @BeforeEach
    void setUp() {
        ruleSet = new AiAuthoringRuleSet(new ObjectMapper());
        ruleSet.init();
    }

    @Test
    void renderSoftPrompt_createExcludesModifyOnlyRule() {
        String prompt = ruleSet.renderSoftPrompt(AiAuthoringRuleSet.ModeCreate);

        assertTrue(prompt.contains(AiAuthoringRuleSet.RuleComponentIdInCatalog));
        assertFalse(prompt.contains(AiAuthoringRuleSet.RuleModifyPreserveUnrelated));
    }

    @Test
    void renderSoftPrompt_modifyIncludesPreservationRule() {
        String prompt = ruleSet.renderSoftPrompt(AiAuthoringRuleSet.ModeModify);

        assertTrue(prompt.contains(AiAuthoringRuleSet.RuleModifyPreserveUnrelated));
    }

    @Test
    void findHard_doesNotReturnSoftRuleWithSameId() {
        var rule = ruleSet.findHard(AiAuthoringRuleSet.RuleComponentIdInCatalog);

        assertTrue(rule.isPresent());
        assertTrue(rule.orElseThrow().isHard());
    }
}
