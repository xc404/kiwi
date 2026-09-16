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
        boolean hasAfter = StringUtils.isNotBlank(afterRef);
        boolean hasBefore = StringUtils.isNotBlank(beforeRef);
        if (hasAfter && hasBefore) {
            spliceBetween(plan, afterRef, newNodeId, beforeRef);
            return;
        }
        if (hasAfter) {
            spliceAfter(plan, afterRef, newNodeId);
            return;
        }
        if (hasBefore) {
            spliceBefore(plan, newNodeId, beforeRef);
        }
    }

    private void spliceBetween(AiWorkflowPlan plan, String afterRef, String newNodeId, String beforeRef) {
        for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
            if (afterRef.equals(flow.getSourceRef()) && beforeRef.equals(flow.getTargetRef())) {
                flow.setTargetRef(newNodeId);
                break;
            }
        }
        ensureEdge(plan, afterRef, newNodeId);
        ensureEdge(plan, newNodeId, beforeRef);
    }

    private void spliceAfter(AiWorkflowPlan plan, String afterRef, String newNodeId) {
        List<AiWorkflowPlan.Flow> outgoing = edgesFrom(plan, afterRef);
        if (outgoing.size() == 1) {
            String oldTarget = outgoing.get(0).getTargetRef();
            outgoing.get(0).setTargetRef(newNodeId);
            ensureEdge(plan, newNodeId, oldTarget);
            return;
        }
        ensureEdge(plan, afterRef, newNodeId);
        if (outgoing.isEmpty()) {
            String next = uniqueDanglingHead(plan, newNodeId);
            if (next != null) {
                ensureEdge(plan, newNodeId, next);
            }
        }
    }

    private void spliceBefore(AiWorkflowPlan plan, String newNodeId, String beforeRef) {
        List<AiWorkflowPlan.Flow> incoming = edgesTo(plan, beforeRef);
        if (incoming.size() == 1) {
            incoming.get(0).setTargetRef(newNodeId);
            ensureEdge(plan, newNodeId, beforeRef);
            return;
        }
        ensureEdge(plan, newNodeId, beforeRef);
        if (incoming.isEmpty()) {
            String prev = uniqueDanglingTail(plan, newNodeId);
            if (prev != null) {
                ensureEdge(plan, prev, newNodeId);
            }
        }
    }

    private List<AiWorkflowPlan.Flow> edgesFrom(AiWorkflowPlan plan, String sourceRef) {
        List<AiWorkflowPlan.Flow> outgoing = new ArrayList<>();
        for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
            if (sourceRef.equals(flow.getSourceRef())) {
                outgoing.add(flow);
            }
        }
        return outgoing;
    }

    private List<AiWorkflowPlan.Flow> edgesTo(AiWorkflowPlan plan, String targetRef) {
        List<AiWorkflowPlan.Flow> incoming = new ArrayList<>();
        for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
            if (targetRef.equals(flow.getTargetRef())) {
                incoming.add(flow);
            }
        }
        return incoming;
    }

    private String uniqueDanglingHead(AiWorkflowPlan plan, String excludeId) {
        List<String> heads = new ArrayList<>();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            if (excludeId.equals(node.getId()) || "startEvent".equals(node.getType())) {
                continue;
            }
            if (edgesTo(plan, node.getId()).isEmpty()) {
                heads.add(node.getId());
            }
        }
        return heads.size() == 1 ? heads.get(0) : null;
    }

    private String uniqueDanglingTail(AiWorkflowPlan plan, String excludeId) {
        List<String> tails = new ArrayList<>();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            if (excludeId.equals(node.getId()) || "endEvent".equals(node.getType())) {
                continue;
            }
            if (edgesFrom(plan, node.getId()).isEmpty()) {
                tails.add(node.getId());
            }
        }
        return tails.size() == 1 ? tails.get(0) : null;
    }

    private boolean hasEdge(AiWorkflowPlan plan, String sourceRef, String targetRef) {
        return plan.getFlows().stream().anyMatch(flow ->
                sourceRef.equals(flow.getSourceRef()) && targetRef.equals(flow.getTargetRef()));
    }

    private void ensureEdge(AiWorkflowPlan plan, String sourceRef, String targetRef) {
        if (StringUtils.isAnyBlank(sourceRef, targetRef) || sourceRef.equals(targetRef) || hasEdge(plan, sourceRef, targetRef)) {
            return;
        }
        addEdge(plan, sourceRef, targetRef);
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
        List<String> predecessors = new ArrayList<>();
        List<String> successors = new ArrayList<>();
        for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
            if (nodeId.equals(flow.getTargetRef()) && !predecessors.contains(flow.getSourceRef())) {
                predecessors.add(flow.getSourceRef());
            }
            if (nodeId.equals(flow.getSourceRef()) && !successors.contains(flow.getTargetRef())) {
                successors.add(flow.getTargetRef());
            }
        }
        plan.getNodes().removeIf(n -> nodeId.equals(n.getId()));
        plan.getFlows().removeIf(f -> nodeId.equals(f.getSourceRef()) || nodeId.equals(f.getTargetRef()));
        boolean linearOrFan = (predecessors.size() == 1 || successors.size() == 1)
                && !predecessors.isEmpty()
                && !successors.isEmpty();
        if (!linearOrFan) {
            return;
        }
        for (String predecessor : predecessors) {
            for (String successor : successors) {
                ensureEdge(plan, predecessor, successor);
            }
        }
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
        if (hasEdge(plan, spec.getSourceRef(), spec.getTargetRef())) {
            return;
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