package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.KiwiBpmnXml;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.model.BpmProcessIoGapAnalysis;
import com.kiwi.project.bpm.model.BpmProcessIoInventory;
import com.kiwi.project.bpm.model.BpmProcessIoInventory.Param;
import com.kiwi.project.bpm.model.BpmProcessIoInventory.ValueKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BpmProcessIoAnalysisServiceTest {

    @Mock
    BpmComponentService bpmComponentService;

    BpmProcessIoAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new BpmProcessIoAnalysisService(bpmComponentService);
        lenient().when(bpmComponentService.resolveComponentById("classpath_uuidGenerate")).thenReturn(uuidComponent());
        lenient().when(bpmComponentService.resolveComponentById("classpath_httpRequest")).thenReturn(httpComponent());
    }

    @Test
    void uuidThenHttpUrlFromUuid_satisfiedAndNoStartVars() {
        String xml = processXml("""
                <bpmn:startEvent id="StartEvent_1"/>
                <bpmn:serviceTask id="Activity_uuid" name="UUID" kiwi:componentId="classpath_uuidGenerate"/>
                <bpmn:serviceTask id="Activity_http" name="HTTP" kiwi:componentId="classpath_httpRequest">
                  <bpmn:extensionElements>
                    <camunda:inputOutput>
                      <camunda:inputParameter name="url">${uuid}</camunda:inputParameter>
                    </camunda:inputOutput>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
                <bpmn:endEvent id="EndEvent_1"/>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_uuid"/>
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_uuid" targetRef="Activity_http"/>
                <bpmn:sequenceFlow id="Flow_3" sourceRef="Activity_http" targetRef="EndEvent_1"/>
                """);
        BpmProcessIoInventory inventory = service.analyzeInventory(xml);
        assertEquals(List.of("Activity_uuid", "Activity_http"), nodeIds(inventory));
        Param url = input(inventory, "Activity_http", "url");
        assertEquals(ValueKind.Expression, url.getValueKind());
        assertTrue(url.isFilled());
        assertTrue(url.isSatisfiedByUpstream());
        assertTrue(inventory.getStartVariables().isEmpty());
    }

    @Test
    void httpUrlExpression_becomesStartVariable() {
        String xml = singleHttp("""
                <camunda:inputParameter name="url">${requestUrl}</camunda:inputParameter>
                """);
        BpmProcessIoInventory inventory = service.analyzeInventory(xml);
        Param url = input(inventory, "Activity_http", "url");
        assertEquals(ValueKind.Expression, url.getValueKind());
        assertFalse(url.isSatisfiedByUpstream());
        assertEquals(1, inventory.getStartVariables().size());
        assertEquals("requestUrl", inventory.getStartVariables().get(0).getKey());
        assertEquals("Activity_http", inventory.getStartVariables().get(0).getNodeId());
        assertEquals("url", inventory.getStartVariables().get(0).getParameterKey());
    }

    @Test
    void httpUrlLiteral_notAStartVariable() {
        String xml = singleHttp("""
                <camunda:inputParameter name="url">https://example.test</camunda:inputParameter>
                """);
        BpmProcessIoInventory inventory = service.analyzeInventory(xml);
        Param url = input(inventory, "Activity_http", "url");
        assertEquals(ValueKind.Literal, url.getValueKind());
        assertTrue(url.isFilled());
        assertTrue(inventory.getStartVariables().isEmpty());
        BpmProcessIoGapAnalysis gap = service.analyzeComponentIoGaps(xml);
        assertTrue(gap.getProcessInputs().isEmpty());
    }

    @Test
    void kiwiComponentIdWithoutCamundaProperty() {
        String xml = processXml("""
                <bpmn:startEvent id="StartEvent_1"/>
                <bpmn:serviceTask id="Activity_http" name="HTTP" kiwi:componentId="classpath_httpRequest">
                  <bpmn:extensionElements>
                    <camunda:inputOutput>
                      <camunda:inputParameter name="url">${requestUrl}</camunda:inputParameter>
                    </camunda:inputOutput>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
                <bpmn:endEvent id="EndEvent_1"/>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_http"/>
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_http" targetRef="EndEvent_1"/>
                """);
        BpmProcessIoInventory inventory = service.analyzeInventory(xml);
        assertEquals("classpath_httpRequest", inventory.getNodes().get(0).getComponentId());
        assertEquals("HTTP 请求", inventory.getNodes().get(0).getComponentName());
    }

    @Test
    void missingRequiredUrl_implicitStartVariable() {
        String xml = processXml("""
                <bpmn:startEvent id="StartEvent_1"/>
                <bpmn:serviceTask id="Activity_http" name="HTTP" kiwi:componentId="classpath_httpRequest"/>
                <bpmn:endEvent id="EndEvent_1"/>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_http"/>
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_http" targetRef="EndEvent_1"/>
                """);
        BpmProcessIoInventory inventory = service.analyzeInventory(xml);
        Param url = input(inventory, "Activity_http", "url");
        assertFalse(url.isFilled());
        assertEquals(ValueKind.Empty, url.getValueKind());
        assertEquals(List.of("url"), url.getExpressionRefs());
        assertEquals("url", inventory.getStartVariables().get(0).getKey());
    }

    @Test
    void twoSequentialTasks_keepFlowOrder() {
        String xml = processXml("""
                <bpmn:startEvent id="StartEvent_1"/>
                <bpmn:serviceTask id="Activity_a" name="A" kiwi:componentId="classpath_uuidGenerate"/>
                <bpmn:serviceTask id="Activity_b" name="B" kiwi:componentId="classpath_httpRequest">
                  <bpmn:extensionElements>
                    <camunda:inputOutput>
                      <camunda:inputParameter name="url">https://example.test</camunda:inputParameter>
                    </camunda:inputOutput>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
                <bpmn:endEvent id="EndEvent_1"/>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_a"/>
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_a" targetRef="Activity_b"/>
                <bpmn:sequenceFlow id="Flow_3" sourceRef="Activity_b" targetRef="EndEvent_1"/>
                """);
        BpmProcessIoInventory inventory = service.analyzeInventory(xml);
        assertEquals(List.of("Activity_a", "Activity_b"), nodeIds(inventory));
    }

    private Param input(BpmProcessIoInventory inventory, String nodeId, String key) {
        return inventory.getNodes().stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .flatMap(n -> n.getInputs().stream())
                .filter(p -> key.equals(p.getKey()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> nodeIds(BpmProcessIoInventory inventory) {
        return inventory.getNodes().stream().map(BpmProcessIoInventory.Node::getNodeId).toList();
    }

    private String singleHttp(String inputParameters) {
        return processXml("""
                <bpmn:startEvent id="StartEvent_1"/>
                <bpmn:serviceTask id="Activity_http" name="HTTP" kiwi:componentId="classpath_httpRequest">
                  <bpmn:extensionElements>
                    <camunda:inputOutput>
                      %s
                    </camunda:inputOutput>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
                <bpmn:endEvent id="EndEvent_1"/>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Activity_http"/>
                <bpmn:sequenceFlow id="Flow_2" sourceRef="Activity_http" targetRef="EndEvent_1"/>
                """.formatted(inputParameters));
    }

    private String processXml(String processBody) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                                  xmlns:kiwi="%s"
                                  id="Definitions_1" targetNamespace="tns">
                  <bpmn:process id="p1" name="demo" isExecutable="true">
                    %s
                  </bpmn:process>
                </bpmn:definitions>
                """.formatted(KiwiBpmnXml.Namespace, processBody);
    }

    private BpmComponent uuidComponent() {
        BpmComponent c = new BpmComponent();
        c.setId("classpath_uuidGenerate");
        c.setName("生成 UUID");
        BpmComponentParameter uuid = new BpmComponentParameter();
        uuid.setKey("uuid");
        uuid.setName("uuid");
        uuid.setDefaultValue("uuid");
        c.setOutputParameters(List.of(uuid));
        return c;
    }

    private BpmComponent httpComponent() {
        BpmComponent c = new BpmComponent();
        c.setId("classpath_httpRequest");
        c.setName("HTTP 请求");
        BpmComponentParameter url = new BpmComponentParameter();
        url.setKey("url");
        url.setName("url");
        url.setRequired(true);
        BpmComponentParameter method = new BpmComponentParameter();
        method.setKey("method");
        method.setName("method");
        method.setDefaultValue("GET");
        BpmComponentParameter status = new BpmComponentParameter();
        status.setKey("statusCode");
        status.setDefaultValue("statusCode");
        c.setInputParameters(List.of(url, method));
        c.setOutputParameters(List.of(status));
        return c;
    }
}
