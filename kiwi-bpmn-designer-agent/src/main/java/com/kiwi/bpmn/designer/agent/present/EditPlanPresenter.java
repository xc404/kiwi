package com.kiwi.bpmn.designer.agent.present;

import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.assistant.AssistantPlan;
import com.kiwi.bpmn.designer.agent.model.EditOperation;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import com.kiwi.bpmn.designer.agent.model.FlowSpec;
import com.kiwi.bpmn.designer.agent.model.NodeSpec;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 {@link EditPlan} 转为用户可读展示视图。
 */
@Component
@RequiredArgsConstructor
public class EditPlanPresenter {

    private static final Map<String, String> NodeTypeLabels = Map.of(
            "startEvent", "开始事件",
            "endEvent", "结束事件",
            "serviceTask", "服务任务",
            "userTask", "用户任务",
            "exclusiveGateway", "排他网关",
            "parallelGateway", "并行网关");

    private final AssistantBpmnToPlan bpmnToPlan;

    public PlanDisplayView present(EditPlan plan, String baseBpmnXml, String llmSummary) {
        PlanDisplayView view = new PlanDisplayView();
        if (plan == null) {
            view.setSummary(StringUtils.defaultIfBlank(llmSummary, "暂无变更计划"));
            view.setOperationCount(0);
            return view;
        }
        Map<String, String> nameById = buildNameIndex(plan, baseBpmnXml);
        List<EditOperation> operations = plan.getOperations() == null ? List.of() : plan.getOperations();
        view.setOperationCount(operations.size());
        view.setSummary(resolveSummary(plan, llmSummary));

        int index = 1;
        for (EditOperation op : operations) {
            PlanStepView step = toStep(op, index++, nameById);
            if (step != null) {
                view.getSteps().add(step);
            }
        }
        return view;
    }

    private String resolveSummary(EditPlan plan, String llmSummary) {
        if (StringUtils.isNotBlank(plan.getSummary())) {
            return plan.getSummary().trim();
        }
        if (StringUtils.isNotBlank(llmSummary)) {
            return llmSummary.trim();
        }
        int count = plan.getOperations() == null ? 0 : plan.getOperations().size();
        if (count == 0) {
            return "暂无具体变更步骤，请补充说明或拒绝后重试。";
        }
        return "将对当前流程执行 " + count + " 项变更，请确认后执行。";
    }

    private PlanStepView toStep(EditOperation op, int index, Map<String, String> nameById) {
        if (op == null || StringUtils.isBlank(op.getOp())) {
            return null;
        }
        return switch (op.getOp()) {
            case "addNode" -> addNodeStep(op, index, nameById);
            case "updateNode" -> updateNodeStep(op, index, nameById);
            case "removeNode" -> removeNodeStep(op, index, nameById);
            case "addFlow" -> addFlowStep(op, index, nameById);
            case "removeFlow" -> removeFlowStep(op, index, nameById);
            case "setProcessMeta" -> metaStep(op, index);
            default -> unknownStep(op, index);
        };
    }

    private PlanStepView addNodeStep(EditOperation op, int index, Map<String, String> nameById) {
        NodeSpec node = op.getNode();
        PlanStepView step = new PlanStepView();
        step.setIndex(index);
        step.setKind("add");
        String typeLabel = nodeTypeLabel(node == null ? null : node.getType());
        String displayName = nodeDisplayName(node, nameById);
        step.setTitle("添加" + typeLabel + "「" + displayName + "」");
        StringBuilder detail = new StringBuilder();
        if (StringUtils.isNotBlank(op.getAfterRef())) {
            detail.append("接在「").append(resolveRefLabel(op.getAfterRef(), nameById)).append("」之后");
            step.setTargetRef(op.getAfterRef());
        } else if (StringUtils.isNotBlank(op.getBeforeRef())) {
            detail.append("接在「").append(resolveRefLabel(op.getBeforeRef(), nameById)).append("」之前");
            step.setTargetRef(op.getBeforeRef());
        }
        if (node != null && StringUtils.isNotBlank(node.getComponentId())) {
            if (detail.length() > 0) {
                detail.append("；");
            }
            detail.append("使用组件「").append(componentLabel(node.getComponentId())).append("」");
        }
        if (detail.length() > 0) {
            step.setDetail(detail.toString());
        }
        return step;
    }

    private PlanStepView updateNodeStep(EditOperation op, int index, Map<String, String> nameById) {
        PlanStepView step = new PlanStepView();
        step.setIndex(index);
        step.setKind("update");
        String nodeId = StringUtils.defaultIfBlank(op.getNodeId(), op.getNode() != null ? op.getNode().getId() : null);
        step.setTargetRef(nodeId);
        String label = resolveRefLabel(nodeId, nameById);
        step.setTitle("修改「" + label + "」");
        step.setDetail(describePatch(op.getPatch()));
        return step;
    }

    private PlanStepView removeNodeStep(EditOperation op, int index, Map<String, String> nameById) {
        PlanStepView step = new PlanStepView();
        step.setIndex(index);
        step.setKind("remove");
        step.setTargetRef(op.getNodeId());
        step.setTitle("删除「" + resolveRefLabel(op.getNodeId(), nameById) + "」");
        return step;
    }

    private PlanStepView addFlowStep(EditOperation op, int index, Map<String, String> nameById) {
        FlowSpec flow = op.getFlow();
        PlanStepView step = new PlanStepView();
        step.setIndex(index);
        step.setKind("connect");
        if (flow == null) {
            step.setTitle("添加连线");
            return step;
        }
        String source = resolveRefLabel(flow.getSourceRef(), nameById);
        String target = resolveRefLabel(flow.getTargetRef(), nameById);
        step.setTitle("连接「" + source + "」→「" + target + "」");
        if (StringUtils.isNotBlank(flow.getCondition())) {
            step.setDetail("条件：" + abbreviate(flow.getCondition(), 80));
        }
        return step;
    }

    private PlanStepView removeFlowStep(EditOperation op, int index, Map<String, String> nameById) {
        PlanStepView step = new PlanStepView();
        step.setIndex(index);
        step.setKind("remove");
        step.setTargetRef(op.getFlowId());
        step.setTitle("删除连线");
        if (StringUtils.isNotBlank(op.getFlowId())) {
            step.setDetail("连线 id：" + shortId(op.getFlowId()));
        }
        return step;
    }

    private PlanStepView metaStep(EditOperation op, int index) {
        PlanStepView step = new PlanStepView();
        step.setIndex(index);
        step.setKind("meta");
        String name = StringUtils.defaultIfBlank(op.getName(), "新流程");
        step.setTitle("将流程名称改为「" + name + "」");
        return step;
    }

    private PlanStepView unknownStep(EditOperation op, int index) {
        PlanStepView step = new PlanStepView();
        step.setIndex(index);
        step.setKind("meta");
        step.setTitle("执行操作：" + op.getOp());
        return step;
    }

    private String describePatch(NodeSpec patch) {
        if (patch == null) {
            return "更新节点属性";
        }
        List<String> parts = new java.util.ArrayList<>();
        if (StringUtils.isNotBlank(patch.getName())) {
            parts.add("名称改为「" + patch.getName() + "」");
        }
        if (StringUtils.isNotBlank(patch.getComponentId())) {
            parts.add("组件改为「" + componentLabel(patch.getComponentId()) + "」");
        }
        if (patch.getParameters() != null && !patch.getParameters().isEmpty()) {
            String params = patch.getParameters().entrySet().stream()
                    .limit(4)
                    .map(e -> e.getKey() + "=" + abbreviate(String.valueOf(e.getValue()), 24))
                    .collect(Collectors.joining("，"));
            parts.add("参数：" + params);
        }
        return parts.isEmpty() ? "更新节点属性" : String.join("；", parts);
    }

    private Map<String, String> buildNameIndex(EditPlan plan, String baseBpmnXml) {
        Map<String, String> nameById = new LinkedHashMap<>();
        bpmnToPlan.parse(baseBpmnXml).ifPresent(base -> indexPlanNodes(base, nameById));
        if (plan.getOperations() != null) {
            for (EditOperation op : plan.getOperations()) {
                if (op.getNode() != null && StringUtils.isNotBlank(op.getNode().getId())) {
                    registerNodeName(nameById, op.getNode());
                }
                if (op.getPatch() != null && StringUtils.isNotBlank(op.getNodeId())) {
                    if (StringUtils.isNotBlank(op.getPatch().getName())) {
                        nameById.put(op.getNodeId(), op.getPatch().getName());
                    }
                }
            }
        }
        return nameById;
    }

    private void indexPlanNodes(AssistantPlan plan, Map<String, String> nameById) {
        if (plan.getNodes() == null) {
            return;
        }
        for (AssistantPlan.Node node : plan.getNodes()) {
            if (StringUtils.isBlank(node.getId())) {
                continue;
            }
            if (StringUtils.isNotBlank(node.getName())) {
                nameById.put(node.getId(), node.getName());
            } else {
                nameById.putIfAbsent(node.getId(), defaultNodeLabel(node.getType(), node.getId()));
            }
        }
        if (StringUtils.isNotBlank(plan.getProcessId())) {
            nameById.putIfAbsent(plan.getProcessId(), StringUtils.defaultIfBlank(plan.getName(), "流程"));
        }
    }

    private void registerNodeName(Map<String, String> nameById, NodeSpec node) {
        if (StringUtils.isBlank(node.getId())) {
            return;
        }
        if (StringUtils.isNotBlank(node.getName())) {
            nameById.put(node.getId(), node.getName());
        } else {
            nameById.putIfAbsent(node.getId(), defaultNodeLabel(node.getType(), node.getId()));
        }
    }

    private String resolveRefLabel(String id, Map<String, String> nameById) {
        if (StringUtils.isBlank(id)) {
            return "未指定节点";
        }
        return nameById.getOrDefault(id, defaultNodeLabel(null, id));
    }

    private String nodeDisplayName(NodeSpec node, Map<String, String> nameById) {
        if (node == null) {
            return "新节点";
        }
        if (StringUtils.isNotBlank(node.getName())) {
            return node.getName();
        }
        if (StringUtils.isNotBlank(node.getId())) {
            return nameById.getOrDefault(node.getId(), defaultNodeLabel(node.getType(), node.getId()));
        }
        return defaultNodeLabel(node.getType(), null);
    }

    private String defaultNodeLabel(String type, String id) {
        if (StringUtils.isNotBlank(type)) {
            String label = nodeTypeLabel(type);
            if (StringUtils.isNotBlank(id)) {
                return label + " " + shortId(id);
            }
            return label;
        }
        if (StringUtils.isNotBlank(id)) {
            if (id.toLowerCase().contains("start")) {
                return "开始";
            }
            if (id.toLowerCase().contains("end")) {
                return "结束";
            }
            return "节点 " + shortId(id);
        }
        return "节点";
    }

    private String nodeTypeLabel(String type) {
        if (StringUtils.isBlank(type)) {
            return "节点";
        }
        return NodeTypeLabels.getOrDefault(type, type);
    }

    private String componentLabel(String componentId) {
        if (StringUtils.isBlank(componentId)) {
            return "未指定组件";
        }
        return componentId;
    }

    private String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 12 ? id : id.substring(0, 12) + "…";
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
