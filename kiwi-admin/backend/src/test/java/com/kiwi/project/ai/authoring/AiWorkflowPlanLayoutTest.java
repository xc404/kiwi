package com.kiwi.project.ai.authoring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiWorkflowPlanLayoutTest {

    @Test
    void placesNodesLeftToRightBySequenceFlow_notListOrder() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent"),
                node("EndEvent_1", "endEvent"),
                node("Activity_del", "serviceTask")));
        plan.setFlows(List.of(
                flow("Flow_1", "StartEvent_1", "Activity_del"),
                flow("Flow_2", "Activity_del", "EndEvent_1")));

        Map<String, AiWorkflowPlanLayout.Box> boxes = new AiWorkflowPlanLayout().layout(plan);

        assertTrue(boxes.get("StartEvent_1").x() < boxes.get("Activity_del").x());
        assertTrue(boxes.get("Activity_del").x() < boxes.get("EndEvent_1").x());
        assertEquals(boxes.get("StartEvent_1").centerY(), boxes.get("Activity_del").centerY());
        assertEquals(boxes.get("Activity_del").centerY(), boxes.get("EndEvent_1").centerY());
    }

    @Test
    void stacksExclusiveGatewayBranchesVertically() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
        plan.setNodes(List.of(
                node("StartEvent_1", "startEvent"),
                node("Gateway_1", "exclusiveGateway"),
                node("EndEvent_Yes", "endEvent"),
                node("EndEvent_No", "endEvent")));
        plan.setFlows(List.of(
                flow("Flow_Start", "StartEvent_1", "Gateway_1"),
                flow("Flow_Yes", "Gateway_1", "EndEvent_Yes"),
                flow("Flow_No", "Gateway_1", "EndEvent_No")));

        Map<String, AiWorkflowPlanLayout.Box> boxes = new AiWorkflowPlanLayout().layout(plan);

        assertEquals(boxes.get("EndEvent_Yes").x(), boxes.get("EndEvent_No").x());
        assertTrue(boxes.get("EndEvent_Yes").y() != boxes.get("EndEvent_No").y());
        assertTrue(boxes.get("Gateway_1").x() < boxes.get("EndEvent_Yes").x());
    }

    private AiWorkflowPlan.Node node(String id, String type) {
        AiWorkflowPlan.Node node = new AiWorkflowPlan.Node();
        node.setId(id);
        node.setType(type);
        return node;
    }

    private AiWorkflowPlan.Flow flow(String id, String sourceRef, String targetRef) {
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId(id);
        flow.setSourceRef(sourceRef);
        flow.setTargetRef(targetRef);
        return flow;
    }
}
