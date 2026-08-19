package com.kiwi.bpmn.designer.agent.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.assistant.AssistantPlanCompiler;
import com.kiwi.bpmn.designer.agent.model.EditOperation;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.model.NodeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanApplicatorTest {

    private EditPlanApplicator applicator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        applicator = new EditPlanApplicator(new AssistantBpmnToPlan(), new AssistantPlanCompiler(objectMapper));
    }

    @Test
    void addServiceTaskToMinimalProcess() {
        String base = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:kiwi="http://kiwi.com/bpmn" id="D1" targetNamespace="http://kiwi.io/test">
                  <bpmn:process id="P1" name="Test" isExecutable="true">
                    <bpmn:startEvent id="Start_1"/>
                    <bpmn:endEvent id="End_1"/>
                    <bpmn:sequenceFlow id="F1" sourceRef="Start_1" targetRef="End_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        EditPlan plan = new EditPlan();
        plan.setProcessId("P1");
        EditOperation removeFlow = new EditOperation();
        removeFlow.setOp("removeFlow");
        removeFlow.setFlowId("F1");
        EditOperation addNode = new EditOperation();
        addNode.setOp("addNode");
        NodeSpec node = new NodeSpec();
        node.setId("Task_http");
        node.setType("serviceTask");
        node.setName("HTTP");
        node.setComponentId("httpRequest");
        node.setParameters(Map.of("url", "https://example.com"));
        addNode.setNode(node);
        addNode.setAfterRef("Start_1");
        addNode.setBeforeRef("Start_1");
        EditOperation addFlow = new EditOperation();
        addFlow.setOp("addFlow");
        addFlow.setFlow(new com.kiwi.bpmn.designer.agent.model.FlowSpec());
        addFlow.getFlow().setId("F2");
        addFlow.getFlow().setSourceRef("Task_http");
        addFlow.getFlow().setTargetRef("End_1");
        plan.setOperations(List.of(removeFlow, addNode, addFlow));
        var out = applicator.apply(base, plan);
        assertTrue(out.isPresent());
        assertTrue(out.get().contains("Task_http"));
        assertTrue(out.get().contains("httpRequest"));
    }
}
