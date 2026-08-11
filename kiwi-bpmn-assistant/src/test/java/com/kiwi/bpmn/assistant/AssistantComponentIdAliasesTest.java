package com.kiwi.bpmn.assistant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantComponentIdAliasesTest {

    @Test
    void alternateAndSameComponent() {
        assertEquals("plugin_shell", AssistantComponentIdAliases.alternateId("classpath_shell"));
        assertEquals("classpath_shell", AssistantComponentIdAliases.alternateId("plugin_shell"));
        assertTrue(AssistantComponentIdAliases.sameComponent("plugin_shell", "classpath_shell"));
        assertFalse(AssistantComponentIdAliases.sameComponent("plugin_shell", "classpath_httpRequest"));
        assertEquals("shell", AssistantComponentIdAliases.beanName("plugin_shell"));
    }
}
