package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmTemplatePackManifestScanner;
import com.kiwi.project.system.ai.BpmDesignerXmlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmAiWorkflowValidatorTest {

    @Mock
    BpmComponentService bpmComponentService;
    @Mock
    BpmComponentPluginLoader bpmComponentPluginLoader;

    BpmAiWorkflowValidator validator;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(bpmComponentPluginLoader.buildPluginJarIndex()).thenReturn(java.util.Map.of());
        org.mockito.Mockito.lenient().when(bpmComponentService.resolveComponentById(anyString())).thenReturn(null);
        ObjectMapper objectMapper = new ObjectMapper();
        AiAuthoringRuleSet ruleSet = new AiAuthoringRuleSet(objectMapper);
        ruleSet.init();
        validator = new BpmAiWorkflowValidator(
                new BpmDesignerXmlValidator(),
                bpmComponentService,
                bpmComponentPluginLoader,
                new BpmTemplatePackManifestScanner(new BpmDesignerXmlValidator()),
                new AiChatProperties(),
                objectMapper,
                ruleSet);
    }

    @Test
    void validate_malformedXml_dispatchRepair() {
        var result = validator.validate("<not-xml", new AiAuthoringCatalog());
        assertEquals(AiAuthoringVariables.DispatchRepair, result.getDispatchCode());
        assertTrue(result.getIssues().stream().anyMatch(i -> BpmAiWorkflowValidator.CodeXmlMalformed.equals(i.getCode())));
    }

    @Test
    void validate_minimalValidWithUnknownComponent_askOrInstall() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:kiwi="http://kiwi.io/schema/bpmn"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:serviceTask id="Activity_1" name="X" kiwi:componentId="plugin_unknownThing"/>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        var result = validator.validate(xml, new AiAuthoringCatalog());
        assertTrue(result.getIssues().stream().anyMatch(i ->
                BpmAiWorkflowValidator.CodeUnknownComponent.equals(i.getCode())
                        || BpmAiWorkflowValidator.CodePluginNotInstalled.equals(i.getCode())));
    }

    @Test
    void validate_missingEnd_hasHardRuleId() {
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        var result = validator.validate(xml, new AiAuthoringCatalog());

        assertTrue(result.getIssues().stream().anyMatch(i ->
                BpmAiWorkflowValidator.CodeNoEnd.equals(i.getCode())
                        && AiAuthoringRuleSet.RuleHasStartAndEnd.equals(i.getRuleId())));
    }

    @Test
    void validate_requiredParam_checksItsOwnComponentNode() {
        BpmComponent component = new BpmComponent();
        component.setId("classpath_httpRequest");
        BpmComponentParameter required = new BpmComponentParameter();
        required.setKey("url");
        required.setRequired(true);
        component.setInputParameters(java.util.List.of(required));
        when(bpmComponentService.resolveComponentById("classpath_httpRequest")).thenReturn(component);

        AiAuthoringCatalog catalog = new AiAuthoringCatalog();
        AiAuthoringCatalog.CatalogComponent installed = new AiAuthoringCatalog.CatalogComponent();
        installed.setId("classpath_httpRequest");
        catalog.setInstalled(java.util.List.of(installed));
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="http://kiwi.io/schema/bpmn"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1"/>
                    <bpmn:serviceTask id="Activity_1" kiwi:componentId="classpath_httpRequest"/>
                    <bpmn:serviceTask id="Activity_2">
                      <bpmn:extensionElements>
                        <camunda:inputOutput>
                          <camunda:inputParameter name="url">https://example.test</camunda:inputParameter>
                        </camunda:inputOutput>
                      </bpmn:extensionElements>
                    </bpmn:serviceTask>
                    <bpmn:endEvent id="EndEvent_1"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_1"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_1" targetRef="Activity_2"/>
                    <bpmn:sequenceFlow id="Flow_3" sourceRef="Activity_2" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        var result = validator.validate(xml, catalog);

        assertTrue(result.getIssues().stream().anyMatch(i ->
                BpmAiWorkflowValidator.CodeMissingRequiredParam.equals(i.getCode())
                        && "Activity_1".equals(i.getElementId())
                        && AiAuthoringRuleSet.RuleRequiredParamsPresent.equals(i.getRuleId())));
        assertEquals(AiAuthoringVariables.DispatchRepair, result.getDispatchCode());
        assertEquals(AiAuthoringVariables.DispatchAsk, validator.toDispatchCode(result.getIssues(), 99));
    }
}
