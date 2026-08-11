package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantRuleSetTest {

    private AssistantRuleSet ruleSet;

    @BeforeEach
    void setUp() {
        ruleSet = new AssistantRuleSet(new ObjectMapper());
        ruleSet.init();
    }

    @Test
    void renderSoftPrompt_createExcludesModifyOnlyRule() {
        String prompt = ruleSet.renderSoftPrompt(AssistantRuleSet.ModeCreate);

        assertTrue(prompt.contains(AssistantRuleSet.RuleComponentIdInCatalog));
        assertTrue(prompt.contains(AssistantRuleSet.RulePlanIrStructure));
        assertTrue(prompt.contains("parameters"));
        assertTrue(prompt.contains("Catalog.components"));
        assertFalse(prompt.contains("installed"));
        assertFalse(prompt.contains("requiresInstall"));
        assertFalse(prompt.contains(AssistantRuleSet.RuleModifyPreserveUnrelated));
    }

    @Test
    void renderSoftPrompt_modifyIncludesPreservationRule() {
        String prompt = ruleSet.renderSoftPrompt(AssistantRuleSet.ModeModify);

        assertTrue(prompt.contains(AssistantRuleSet.RuleModifyPreserveUnrelated));
    }

    @Test
    void findHard_doesNotReturnSoftRuleWithSameId() {
        var rule = ruleSet.findHard(AssistantRuleSet.RuleComponentIdInCatalog);

        assertTrue(rule.isPresent());
        assertTrue(rule.orElseThrow().isHard());
    }
}
