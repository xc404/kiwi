package com.kiwi.bpmn.designer.harness.bpmn;

import com.kiwi.bpmn.designer.harness.model.EditOperation;
import com.kiwi.bpmn.designer.harness.model.FlowSpec;
import com.kiwi.bpmn.designer.harness.model.NodeSpec;
import com.kiwi.project.ai.authoring.AiWorkflowPlan;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Component
public class DesignerHarnessPlanMutator {

    public void apply(AiWorkflowPlan plan, List<EditOperation> operations) {
        if (plan.getNodes() == null) {
            plan.setNodes(new ArrayList<>());
        }
        if (plan.getFlows() == null) {
            plan.setFlows(new ArrayList<>());
        }
        if (operations == null) {
            return;
        }
        for (EditOperation op : operations) {
            applyOne(plan, op);
        }
    }

    private void applyOne(AiWorkflowPlan plan, EditOperation op) {
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
            default -> throw new IllegalArgumentException("未知 op: " + op.getOp());
        }
    }

    private void addNode(AiWorkflowPlan plan, EditOperation op) {
        NodeSpec spec = op.getNode();
        if (spec == null) {
            throw new IllegalArgumentException("addNode 需要 node");
        }
        AiWorkflowPlan.Node node = toPlanNode(spec);
        if ("serviceTask".equals(node.getType()) && StringUtils.isBlank(node.getComponentId())) {
            throw new IllegalArgumentException("serviceTask 需要 componentId，请先 search_components / get_component");
        }
        if (StringUtils.isBlank(node.getId())) {
            node.setId("Node_" + UUID.randomUUID().toString().substring(0, 8));
        }
        if (plan.getNodes().stream().anyMatch(n -> node.getId().equals(n.getId()))) {
            throw new IllegalArgumentException("节点 id 已存在: " + node.getId());
        }
        requireNode(plan, op.getAfterRef());
        requireNode(plan, op.getBeforeRef());
        plan.getNodes().add(insertIndex(plan, op.getAfterRef(), op.getBeforeRef()), node);
        wireInsert(plan, node.getId(), op.getAfterRef(), op.getBeforeRef());
    }

    private int insertIndex(AiWorkflowPlan plan, String afterRef, String beforeRef) {
        if (StringUtils.isNotBlank(afterRef)) {
            for (int i = 0; i < plan.getNodes().size(); i++) {
                if (afterRef.equals(plan.getNodes().get(i).getId())) {
                    return i + 1;
                }
            }
        }
        if (StringUtils.isNotBlank(beforeRef)) {
            for (int i = 0; i < plan.getNodes().size(); i++) {
                if (beforeRef.equals(plan.getNodes().get(i).getId())) {
                    return i;
                }
            }
        }
        return plan.getNodes().size();
    }

    private void requireNode(AiWorkflowPlan plan, String nodeId) {
        if (StringUtils.isBlank(nodeId)) {
            return;
        }
        boolean exists = plan.getNodes().stream().anyMatch(n -> nodeId.equals(n.getId()));
        if (!exists) {
            throw new IllegalArgumentException("找不到锚点节点: " + nodeId);
        }
    }

    private void wireInsert(AiWorkflowPlan plan, String newNodeId, String afterRef, String beforeRef) {
        if (StringUtils.isNotBlank(afterRef) && StringUtils.isNotBlank(beforeRef)) {
            boolean split = false;
            for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
                if (afterRef.equals(flow.getSourceRef()) && beforeRef.equals(flow.getTargetRef())) {
                    flow.setTargetRef(newNodeId);
                    split = true;
                    break;
                }
            }
            if (!split) {
                addEdge(plan, afterRef, newNodeId);
            }
            addEdge(plan, newNodeId, beforeRef);
            return;
        }
        if (StringUtils.isNotBlank(afterRef)) {
            List<AiWorkflowPlan.Flow> outgoing = new ArrayList<>();
            for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
                if (afterRef.equals(flow.getSourceRef())) {
                    outgoing.add(flow);
                }
            }
            if (outgoing.size() == 1) {
                AiWorkflowPlan.Flow flow = outgoing.get(0);
                String oldTarget = flow.getTargetRef();
                flow.setTargetRef(newNodeId);
                addEdge(plan, newNodeId, oldTarget);
            } else {
                addEdge(plan, afterRef, newNodeId);
            }
            return;
        }
        if (StringUtils.isNotBlank(beforeRef)) {
            List<AiWorkflowPlan.Flow> incoming = new ArrayList<>();
            for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
                if (beforeRef.equals(flow.getTargetRef())) {
                    incoming.add(flow);
                }
            }
            if (incoming.size() == 1) {
                AiWorkflowPlan.Flow flow = incoming.get(0);
                flow.setTargetRef(newNodeId);
                addEdge(plan, newNodeId, beforeRef);
            } else {
                addEdge(plan, newNodeId, beforeRef);
            }
        }
    }

    private void addEdge(AiWorkflowPlan plan, String sourceRef, String targetRef) {
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId("Flow_" + UUID.randomUUID().toString().substring(0, 8));
        flow.setSourceRef(sourceRef);
        flow.setTargetRef(targetRef);
        plan.getFlows().add(flow);
    }

    private void removeNode(AiWorkflowPlan plan, String nodeId) {
        if (StringUtils.isBlank(nodeId)) {
            throw new IllegalArgumentException("removeNode 需要 nodeId");
        }
        plan.getNodes().removeIf(n -> nodeId.equals(n.getId()));
        plan.getFlows().removeIf(f -> nodeId.equals(f.getSourceRef()) || nodeId.equals(f.getTargetRef()));
    }

    private void updateNode(AiWorkflowPlan plan, String nodeId, NodeSpec patch) {
        if (StringUtils.isBlank(nodeId) || patch == null) {
            throw new IllegalArgumentException("updateNode 需要 nodeId 与 patch");
        }
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
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
        throw new IllegalArgumentException("找不到节点: " + nodeId);
    }

    private void addFlow(AiWorkflowPlan plan, FlowSpec spec) {
        if (spec == null || StringUtils.isBlank(spec.getSourceRef()) || StringUtils.isBlank(spec.getTargetRef())) {
            throw new IllegalArgumentException("addFlow 需要 sourceRef 与 targetRef");
        }
        AiWorkflowPlan.Flow flow = new AiWorkflowPlan.Flow();
        flow.setId(StringUtils.defaultIfBlank(spec.getId(), "Flow_" + UUID.randomUUID().toString().substring(0, 8)));
        flow.setSourceRef(spec.getSourceRef());
        flow.setTargetRef(spec.getTargetRef());
        flow.setCondition(spec.getCondition());
        plan.getFlows().add(flow);
    }

    private void removeFlow(AiWorkflowPlan plan, String flowId) {
        if (StringUtils.isBlank(flowId)) {
            throw new IllegalArgumentException("removeFlow 需要 flowId");
        }
        plan.getFlows().removeIf(f -> flowId.equals(f.getId()));
    }

    private AiWorkflowPlan.Node toPlanNode(NodeSpec spec) {
        AiWorkflowPlan.Node node = new AiWorkflowPlan.Node();
        node.setId(spec.getId());
        node.setType(StringUtils.defaultIfBlank(spec.getType(), "serviceTask"));
        node.setName(spec.getName());
        node.setComponentId(spec.getComponentId());
        if (spec.getParameters() != null) {
            node.setParameters(new LinkedHashMap<>(spec.getParameters()));
        }
        return node;
    }
}