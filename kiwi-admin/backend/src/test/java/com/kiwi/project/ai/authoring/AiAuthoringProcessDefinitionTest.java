package com.kiwi.project.ai.authoring;

import com.kiwi.project.system.ai.BpmDesignerXmlValidator;
import org.junit.jupiter.api.Test;
import org.operaton.bpm.model.bpmn.Bpmn;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAuthoringProcessDefinitionTest {

    @Test
    void processDefinition_containsInstallDelegateBeforeRevalidation() throws Exception {
        String path = "/bpm/ai/kiwi_ai_workflow_authoring.bpmn";
        String xml;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertTrue(input != null, "缺少内部 authoring BPMN");
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        new BpmDesignerXmlValidator().validate(xml);
        Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertTrue(xml.contains("id=\"Task_Install\""));
        assertTrue(xml.contains("camunda:delegateExpression=\"${aiAuthoringInstallDelegate}\""));
        assertTrue(xml.contains("sourceRef=\"Task_Install\" targetRef=\"Task_Validate\""));
    }
}
