package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantWorkflowValidatorTest {

    @Mock
    AssistantComponentLookup componentLookup;

    AssistantWorkflowValidator validator;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(componentLookup.exists(anyString())).thenReturn(false);
        org.mockito.Mockito.lenient().when(componentLookup.requiredInputKeys(anyString())).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(componentLookup.pluginMissingHint(anyString())).thenReturn(Optional.empty());
        ObjectMapper objectMapper = new ObjectMapper();
        AssistantRuleSet ruleSet = new AssistantRuleSet(objectMapper);
        ruleSet.init();
        validator = new AssistantWorkflowValidator(
                new DefaultAssistantXmlValidator(),
                componentLookup,
                new AssistantProperties(),
                objectMapper,
                ruleSet);
    }

    @Test
    void validate_malformedXml_dispatchRepair() {
        var result = validator.validate("<not-xml", new AssistantCatalog());
        assertEquals(AssistantVariables.DispatchRepair, result.getDispatchCode());
        assertTrue(result.getIssues().stream().anyMatch(i -> AssistantWorkflowValidator.CodeXmlMalformed.equals(i.getCode())));
    }

    @Test
    void validate_minimalValidWithUnknownComponent_askOrInstall() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:kiwi="http://kiwi.com/bpmn"
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
        var result = validator.validate(xml, new AssistantCatalog());
        assertTrue(result.getIssues().stream().anyMatch(i ->
                AssistantWorkflowValidator.CodeUnknownComponent.equals(i.getCode())
                        || AssistantWorkflowValidator.CodePluginNotInstalled.equals(i.getCode())));
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

        var result = validator.validate(xml, new AssistantCatalog());

        assertTrue(result.getIssues().stream().anyMatch(i ->
                AssistantWorkflowValidator.CodeNoEnd.equals(i.getCode())
                        && AssistantRuleSet.RuleHasStartAndEnd.equals(i.getRuleId())));
    }

    @Test
    void validate_requiredParam_checksItsOwnComponentNode() {
        when(componentLookup.exists("classpath_httpRequest")).thenReturn(true);
        when(componentLookup.requiredInputKeys("classpath_httpRequest")).thenReturn(List.of("url"));

        AssistantCatalog catalog = new AssistantCatalog();
        AssistantCatalog.CatalogComponent installed = new AssistantCatalog.CatalogComponent();
        installed.setId("classpath_httpRequest");
        catalog.setInstalled(List.of(installed));
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="http://kiwi.com/bpmn"
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
                AssistantWorkflowValidator.CodeMissingRequiredParam.equals(i.getCode())
                        && "Activity_1".equals(i.getElementId())
                        && AssistantRuleSet.RuleRequiredParamsPresent.equals(i.getRuleId())));
        assertEquals(AssistantVariables.DispatchRepair, result.getDispatchCode());
        assertEquals(AssistantVariables.DispatchAsk, validator.toDispatchCode(result.getIssues(), 99));
    }
}
