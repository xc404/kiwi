package com.kiwi.bpmn.designer.harness.bpmn;

import com.kiwi.bpmn.designer.harness.model.EditOperation;
import com.kiwi.bpmn.designer.harness.model.FlowSpec;
import com.kiwi.bpmn.designer.harness.model.NodeSpec;
import com.kiwi.project.ai.authoring.AiWorkflowPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignerHarnessPlanMutatorTest {

    private final DesignerHarnessPlanMutator mutator = new DesignerHarnessPlanMutator();

    @Test
    void addNode_afterDanglingPredecessor_stitchesToUniqueHead() {
        AiWorkflowPlan plan = disconnectedWriteAndDelete();

        mutator.apply(plan, List.of(addService("ServiceTask_readFile", "写文件之后", "ServiceTask_writeFile", null)));

        assertLinear(plan, "StartEvent_1", "ServiceTask_writeFile", "ServiceTask_readFile", "ServiceTask_delFile", "EndEvent_1");
    }

    @Test
    void addNode_beforeDanglingSuccessor_stitchesFromUniqueTail() {
        AiWorkflowPlan plan = disconnectedWriteAndDelete();

        mutator.apply(plan, List.of(addService("ServiceTask_readFile", "删文件之前", null, "ServiceTask_delFile")));

        assertLinear(plan, "StartEvent_1", "ServiceTask_writeFile", "ServiceTask_readFile", "ServiceTask_delFile", "EndEvent_1");
    }

    @Test
    void addNode_afterAndBefore_whenDirectFlowMissing() {
        AiWorkflowPlan plan = disconnectedWriteAndDelete();

        mutator.apply(plan, List.of(addService(
                "ServiceTask_readFile", "读文件", "ServiceTask_writeFile", "ServiceTask_delFile")));

        assertLinear(plan, "StartEvent_1", "ServiceTask_writeFile", "ServiceTask_readFile", "ServiceTask_delFile", "EndEvent_1");
    }

    @Test
    void addNode_thenDuplicateAddFlow_doesNotCreateSecondEdge() {
        AiWorkflowPlan plan = disconnectedWriteAndDelete();
        EditOperation add = addService(
                "ServiceTask_readFile", "读文件", "ServiceTask_writeFile", "ServiceTask_delFile");
        EditOperation duplicateFlow = new EditOperation();
        duplicateFlow.setOp("addFlow");
        FlowSpec flow = new FlowSpec();
        flow.setId("Flow_read_2_del");
        flow.setSourceRef("ServiceTask_readFile");
        flow.setTargetRef("ServiceTask_delFile");
        duplicateFlow.setFlow(flow);

        mutator.apply(plan, List.of(add, duplicateFlow));

        long readToDel = plan.getFlows().stream()
                .filter(f -> "ServiceTask_readFile".equals(f.getSourceRef())
                        && "ServiceTask_delFile".equals(f.getTargetRef()))
                .count();
        assertEquals(1, readToDel);
        assertLinear(plan, "StartEvent_1", "ServiceTask_writeFile", "ServiceTask_readFile", "ServiceTask_delFile", "EndEvent_1");
    }

    @Test
    void addNode_afterGateway_doesNotStealBranches() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
        addNode(plan, "StartEvent_1", "startEvent");
        addNode(plan, "Gateway_1", "exclusiveGateway");
        addNode(plan, "EndEvent_Yes", "endEvent");
        addNode(plan, "EndEvent_No", "endEvent");
        addFlow(plan, "Flow_s", "StartEvent_1", "Gateway_1");
        addFlow(plan, "Flow_yes", "Gateway_1", "EndEvent_Yes");
        addFlow(plan, "Flow_no", "Gateway_1", "EndEvent_No");

        mutator.apply(plan, List.of(addService("Activity_extra", "额外", "Gateway_1", null)));

        assertTrue(hasEdge(plan, "Gateway_1", "EndEvent_Yes"));
        assertTrue(hasEdge(plan, "Gateway_1", "EndEvent_No"));
        assertTrue(hasEdge(plan, "Gateway_1", "Activity_extra"));
        assertTrue(plan.getFlows().stream().noneMatch(f -> "Activity_extra".equals(f.getSourceRef())));
    }

    @Test
    void removeNode_bridgesLinearChain() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
        addNode(plan, "StartEvent_1", "startEvent");
        addNode(plan, "Activity_mid", "userTask");
        addNode(plan, "EndEvent_1", "endEvent");
        addFlow(plan, "Flow_1", "StartEvent_1", "Activity_mid");
        addFlow(plan, "Flow_2", "Activity_mid", "EndEvent_1");

        EditOperation remove = new EditOperation();
        remove.setOp("removeNode");
        remove.setNodeId("Activity_mid");
        mutator.apply(plan, List.of(remove));

        assertEquals(List.of("StartEvent_1", "EndEvent_1"),
                plan.getNodes().stream().map(AiWorkflowPlan.Node::getId).toList());
        assertTrue(hasEdge(plan, "StartEvent_1", "EndEvent_1"));
        assertEquals(1, plan.getFlows().size());
    }

    private AiWorkflowPlan disconnectedWriteAndDelete() {
        AiWorkflowPlan plan = new AiWorkflowPlan();
        addNode(plan, "StartEvent_1", "startEvent");
        addNode(plan, "ServiceTask_writeFile", "serviceTask");
        addNode(plan, "ServiceTask_delFile", "serviceTask");
        addNode(plan, "EndEvent_1", "endEvent");
        addFlow(plan, "Flow_start_2_write", "StartEvent_1", "ServiceTask_writeFile");
        addFlow(plan, "Flow_del_2_end", "ServiceTask_delFile", "EndEvent_1");
        return plan;
    }

    private void assertLinear(AiWorkflowPlan plan, String... ids) {
        for (int i = 0; i < ids.length - 1; i++) {
            assertTrue(hasEdge(plan, ids[i], ids[i + 1]), ids[i] + " → " + ids[i + 1]);
        }
        assertEquals(ids.length - 1, plan.getFlows().size());
    }

    private boolean hasEdge(AiWorkflowPlan plan, String source, String target) {
        return plan.getFlows().stream()
                .anyMatch(f -> source.equals(f.getSourceRef()) && target.equals(f.getTargetRef()));
    }

    private EditOperation addService(String id, String name, String afterRef, String beforeRef) {
        EditOperation op = new EditOperation();
        op.setOp("addNode");
        op.setAfterRef(afterRef);
        op.setBeforeRef(beforeRef);
        NodeSpec node = new NodeSpec();
        node.setId(id);
        node.setType("serviceTask");
        node.setName(name);
        node.setComponentId("plugin_fileRead");
        op.setNode(node);
        return op;
    }

    private void addNode(AiWorkflowPlan plan, String id, String type) {
        AiWorkflowPlan.Node node = new AiWorkflowPlan.Node();
        node.setId(id);
        node.setType(type);
        if ("serviceTask".equals(type)) {
            node.setComponentId("plugin_" + id);
        }
        plan.getNodes().add(node);
    }

    private void addFlow(AiWorkflowPlan plan, String id, String source, String target) {
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId(id);
        flow.setSourceRef(source);
        flow.setTargetRef(target);
        plan.getFlows().add(flow);
    }
}
