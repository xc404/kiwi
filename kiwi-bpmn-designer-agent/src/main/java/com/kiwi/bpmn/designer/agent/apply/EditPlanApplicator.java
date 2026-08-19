package com.kiwi.bpmn.designer.agent.apply;

import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.assistant.AssistantPlan;
import com.kiwi.bpmn.assistant.AssistantPlanCompiler;
import com.kiwi.bpmn.designer.agent.model.EditOperation;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.model.FlowSpec;
import com.kiwi.bpmn.designer.agent.model.NodeSpec;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 将 {@link EditPlan} 确定性应用到 base BPMN XML（经 AssistantPlan 中转编译）。
 */
@Component
public class EditPlanApplicator {

    private final AssistantBpmnToPlan bpmnToPlan;
    private final AssistantPlanCompiler planCompiler;

    public EditPlanApplicator(AssistantBpmnToPlan bpmnToPlan, AssistantPlanCompiler planCompiler) {
        this.bpmnToPlan = bpmnToPlan;
        this.planCompiler = planCompiler;
    }

    public Optional<String> apply(String baseBpmnXml, EditPlan editPlan) {
        if (editPlan == null || editPlan.getOperations() == null || editPlan.getOperations().isEmpty()) {
            return Optional.ofNullable(StringUtils.isBlank(baseBpmnXml) ? null : baseBpmnXml.trim());
        }
        AssistantPlan plan = bpmnToPlan.parse(baseBpmnXml).orElseGet(AssistantPlan::new);
        if (StringUtils.isNotBlank(editPlan.getProcessId())) {
            plan.setProcessId(editPlan.getProcessId());
        }
        for (EditOperation op : editPlan.getOperations()) {
            applyOne(plan, op);
        }
        try {
            return Optional.of(planCompiler.compile(plan));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void applyOne(AssistantPlan plan, EditOperation op) {
        if (op == null || StringUtils.isBlank(op.getOp())) {
            return;
        }
        switch (op.getOp()) {
            case "addNode" -> addNode(plan, op);
            case "removeNode" -> removeNode(plan, op.getNodeId());
            case "updateNode" -> updateNode(plan, op.getNodeId(), op.getPatch());
            case "addFlow" -> addFlow(plan, op.getFlow());
            case "removeFlow" -> removeFlow(plan, op.getFlowId());
            case "setProcessMeta" -> {
                if (StringUtils.isNotBlank(op.getName())) {
                    plan.setName(op.getName());
                }
            }
            default -> {
                // ignore unknown op
            }
        }
    }

    private void addNode(AssistantPlan plan, EditOperation op) {
        NodeSpec spec = op.getNode();
        if (spec == null) {
            return;
        }
        AssistantPlan.Node node = toPlanNode(spec);
        if (StringUtils.isBlank(node.getId())) {
            node.setId("Node_" + UUID.randomUUID().toString().substring(0, 8));
        }
        plan.getNodes().add(node);
        wireAfterInsert(plan, node.getId(), op.getAfterRef(), op.getBeforeRef());
    }

    private void wireAfterInsert(AssistantPlan plan, String newNodeId, String afterRef, String beforeRef) {
        if (StringUtils.isNotBlank(afterRef)) {
            AssistantPlan.Flow out = new AssistantPlan.Flow();
            out.setId("Flow_" + UUID.randomUUID().toString().substring(0, 8));
            out.setSourceRef(afterRef);
            out.setTargetRef(newNodeId);
            plan.getFlows().add(out);
        }
        if (StringUtils.isNotBlank(beforeRef)) {
            List<AssistantPlan.Flow> toRewire = new ArrayList<>();
            for (AssistantPlan.Flow f : plan.getFlows()) {
                if (beforeRef.equals(f.getSourceRef())) {
                    toRewire.add(f);
                }
            }
            for (AssistantPlan.Flow f : toRewire) {
                f.setSourceRef(newNodeId);
                AssistantPlan.Flow in = new AssistantPlan.Flow();
                in.setId("Flow_" + UUID.randomUUID().toString().substring(0, 8));
                in.setSourceRef(beforeRef);
                in.setTargetRef(newNodeId);
                plan.getFlows().add(in);
            }
            if (toRewire.isEmpty()) {
                AssistantPlan.Flow in = new AssistantPlan.Flow();
                in.setId("Flow_" + UUID.randomUUID().toString().substring(0, 8));
                in.setSourceRef(beforeRef);
                in.setTargetRef(newNodeId);
                plan.getFlows().add(in);
            }
        }
    }

    private void removeNode(AssistantPlan plan, String nodeId) {
        if (StringUtils.isBlank(nodeId)) {
            return;
        }
        plan.getNodes().removeIf(n -> nodeId.equals(n.getId()));
        plan.getFlows().removeIf(f -> nodeId.equals(f.getSourceRef()) || nodeId.equals(f.getTargetRef()));
    }

    private void updateNode(AssistantPlan plan, String nodeId, NodeSpec patch) {
        if (StringUtils.isBlank(nodeId) || patch == null) {
            return;
        }
        for (AssistantPlan.Node node : plan.getNodes()) {
            if (!nodeId.equals(node.getId())) {
                continue;
            }
            if (StringUtils.isNotBlank(patch.getType())) {
                node.setType(patch.getType());
            }
            if (StringUtils.isNotBlank(patch.getName())) {
                node.setName(patch.getName());
            }
            if (StringUtils.isNotBlank(patch.getComponentId())) {
                node.setComponentId(patch.getComponentId());
            }
            if (patch.getParameters() != null && !patch.getParameters().isEmpty()) {
                if (node.getParameters() == null) {
                    node.setParameters(new LinkedHashMap<>());
                }
                node.getParameters().putAll(patch.getParameters());
            }
            return;
        }
    }

    private void addFlow(AssistantPlan plan, FlowSpec spec) {
        if (spec == null || StringUtils.isBlank(spec.getSourceRef()) || StringUtils.isBlank(spec.getTargetRef())) {
            return;
        }
        AssistantPlan.Flow flow = new AssistantPlan.Flow();
        flow.setId(StringUtils.defaultIfBlank(spec.getId(), "Flow_" + UUID.randomUUID().toString().substring(0, 8)));
        flow.setSourceRef(spec.getSourceRef());
        flow.setTargetRef(spec.getTargetRef());
        flow.setCondition(spec.getCondition());
        plan.getFlows().add(flow);
    }

    private void removeFlow(AssistantPlan plan, String flowId) {
        if (StringUtils.isBlank(flowId)) {
            return;
        }
        plan.getFlows().removeIf(f -> flowId.equals(f.getId()));
    }

    private AssistantPlan.Node toPlanNode(NodeSpec spec) {
        AssistantPlan.Node node = new AssistantPlan.Node();
        node.setId(spec.getId());
        node.setType(spec.getType());
        node.setName(spec.getName());
        node.setComponentId(spec.getComponentId());
        if (spec.getParameters() != null) {
            node.setParameters(new LinkedHashMap<>(spec.getParameters()));
        }
        return node;
    }
}
