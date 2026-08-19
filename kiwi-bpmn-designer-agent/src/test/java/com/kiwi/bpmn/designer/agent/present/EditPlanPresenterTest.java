package com.kiwi.bpmn.designer.agent.present;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.designer.agent.model.EditOperation;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.model.FlowSpec;
import com.kiwi.bpmn.designer.agent.model.NodeSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditPlanPresenterTest {

    private static final String MinimalBpmn = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:kiwi="http://kiwi.com/bpmn" id="D1" targetNamespace="http://kiwi.io/test">
              <bpmn:process id="P1" name="Test" isExecutable="true">
                <bpmn:startEvent id="Start_1" name="开始"/>
                <bpmn:endEvent id="End_1" name="结束"/>
                <bpmn:sequenceFlow id="F1" sourceRef="Start_1" targetRef="End_1"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

    private EditPlanPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new EditPlanPresenter(new AssistantBpmnToPlan());
    }

    @Test
    void present_addNodeWithAfterRefAndComponent() {
        EditPlan plan = new EditPlan();
        plan.setSummary("将添加 HTTP 请求节点");
        EditOperation addNode = new EditOperation();
        addNode.setOp("addNode");
        addNode.setAfterRef("Start_1");
        NodeSpec node = new NodeSpec();
        node.setId("Task_http");
        node.setType("serviceTask");
        node.setName("HTTP 请求");
        node.setComponentId("httpRequest");
        addNode.setNode(node);
        plan.setOperations(List.of(addNode));

        PlanDisplayView view = presenter.present(plan, MinimalBpmn, null);

        assertEquals("将添加 HTTP 请求节点", view.getSummary());
        assertEquals(1, view.getOperationCount());
        assertEquals(1, view.getSteps().size());
        PlanStepView step = view.getSteps().get(0);
        assertEquals("add", step.getKind());
        assertTrue(step.getTitle().contains("HTTP 请求"));
        assertTrue(step.getDetail().contains("开始"));
        assertTrue(step.getDetail().contains("httpRequest"));
    }

    @Test
    void present_updateNodeUsesPatchDescription() {
        EditPlan plan = new EditPlan();
        EditOperation update = new EditOperation();
        update.setOp("updateNode");
        update.setNodeId("Start_1");
        NodeSpec patch = new NodeSpec();
        patch.setName("流程起点");
        patch.setParameters(Map.of("timeout", "30"));
        update.setPatch(patch);
        plan.setOperations(List.of(update));

        PlanDisplayView view = presenter.present(plan, MinimalBpmn, "更新开始节点");

        assertEquals("更新开始节点", view.getSummary());
        PlanStepView step = view.getSteps().get(0);
        assertEquals("update", step.getKind());
        assertTrue(step.getTitle().contains("流程起点"));
        assertTrue(step.getDetail().contains("流程起点"));
    }

    @Test
    void present_removeNodeAndAddFlow() {
        EditPlan plan = new EditPlan();
        EditOperation remove = new EditOperation();
        remove.setOp("removeNode");
        remove.setNodeId("End_1");
        EditOperation addFlow = new EditOperation();
        addFlow.setOp("addFlow");
        FlowSpec flow = new FlowSpec();
        flow.setSourceRef("Start_1");
        flow.setTargetRef("End_1");
        addFlow.setFlow(flow);
        plan.setOperations(List.of(remove, addFlow));

        PlanDisplayView view = presenter.present(plan, MinimalBpmn, null);

        assertEquals(2, view.getSteps().size());
        assertTrue(view.getSteps().get(0).getTitle().contains("删除"));
        assertTrue(view.getSteps().get(1).getTitle().contains("连接"));
        assertTrue(view.getSteps().get(1).getTitle().contains("开始"));
    }

    @Test
    void present_setProcessMeta() {
        EditPlan plan = new EditPlan();
        EditOperation meta = new EditOperation();
        meta.setOp("setProcessMeta");
        meta.setName("订单流程");
        plan.setOperations(List.of(meta));

        PlanDisplayView view = presenter.present(plan, MinimalBpmn, null);

        assertEquals("将流程名称改为「订单流程」", view.getSteps().get(0).getTitle());
    }

    @Test
    void present_serializesToJson() throws Exception {
        EditPlan plan = new EditPlan();
        plan.setSummary("测试");
        EditOperation meta = new EditOperation();
        meta.setOp("setProcessMeta");
        meta.setName("A");
        plan.setOperations(List.of(meta));

        PlanDisplayView view = presenter.present(plan, MinimalBpmn, null);
        String json = new ObjectMapper().writeValueAsString(view);

        assertTrue(json.contains("\"summary\":\"测试\""));
        assertTrue(json.contains("\"operationCount\":1"));
    }
}
