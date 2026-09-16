package com.kiwi.bpmn.designer.harness.bpmn;

import com.kiwi.bpmn.designer.harness.model.EditOperation;
import com.kiwi.project.ai.authoring.AiAuthoringCatalog;
import com.kiwi.project.ai.authoring.AiAuthoringValidationIssue;
import com.kiwi.project.ai.authoring.AiWorkflowPlan;
import com.kiwi.project.ai.authoring.AiWorkflowPlanCompiler;
import com.kiwi.project.ai.authoring.BpmAiWorkflowValidator;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DesignerHarnessBpmnApplyService {

    private final DesignerHarnessBpmnReader reader;
    private final DesignerHarnessPlanMutator mutator;
    private final AiWorkflowPlanCompiler planCompiler;
    private final BpmAiWorkflowValidator validator;
    private final BpmComponentService bpmComponentService;
    private final BpmProcessDefinitionDao processDefinitionDao;
    private final BpmProcessDefinitionService processDefinitionService;

    public ApplyResult applyAndSave(String processId, String baseXml, List<EditOperation> operations) {
        DesignerHarnessBpmnReader.ParseResult parsed = reader.read(baseXml);
        if (parsed.plan == null || parsed.plan.getNodes() == null || parsed.plan.getNodes().isEmpty()) {
            return ApplyResult.fail("无法从当前 BPMN 得到可编辑的节点（需要 start/end/task/gateway）");
        }
        List<String> ignored = parsed.warnings == null ? List.of() : parsed.warnings.stream()
                .filter(w -> w != null && w.startsWith("已忽略"))
                .toList();
        if (!ignored.isEmpty()) {
            return ApplyResult.fail("当前图含有本 Agent 还不支持的元素，未保存。请用简单流程图（无子流程/泳道/边界事件）。\n"
                    + String.join("\n", ignored));
        }
        try {
            mutator.apply(parsed.plan, operations);
        } catch (IllegalArgumentException e) {
            return ApplyResult.fail(e.getMessage());
        }
        AiAuthoringCatalog catalog = catalogFor(parsed.plan);
        String xml;
        try {
            xml = planCompiler.compile(parsed.plan, catalog);
        } catch (Exception e) {
            return ApplyResult.fail("编译 BPMN 失败: " + e.getMessage());
        }
        xml = persist(processId, xml);
        List<String> issues = parsed.warnings == null ? new ArrayList<>() : new ArrayList<>(parsed.warnings);
        BpmAiWorkflowValidator.ValidationResult validation = validator.validate(xml, catalog);
        if (validation.getIssues() != null) {
            for (AiAuthoringValidationIssue issue : validation.getIssues()) {
                if (issue != null && StringUtils.isNotBlank(issue.getMessage())) {
                    issues.add(issue.getCode() + ": " + issue.getMessage());
                }
            }
        }
        return ApplyResult.ok(xml, issues);
    }

    private AiAuthoringCatalog catalogFor(AiWorkflowPlan plan) {
        AiAuthoringCatalog catalog = new AiAuthoringCatalog();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            if (node == null || !"serviceTask".equals(node.getType()) || StringUtils.isBlank(node.getComponentId())) {
                continue;
            }
            BpmComponent component = bpmComponentService.resolveComponentById(node.getComponentId());
            AiAuthoringCatalog.CatalogComponent entry = new AiAuthoringCatalog.CatalogComponent();
            entry.setId(node.getComponentId());
            if (component != null) {
                entry.setName(component.getName());
                entry.setDelegateExpression("${" + StringUtils.defaultIfBlank(component.getKey(), beanName(node.getComponentId())) + "}");
            } else {
                entry.setDelegateExpression("${" + beanName(node.getComponentId()) + "}");
            }
            catalog.getInstalled().add(entry);
        }
        return catalog;
    }

    private String beanName(String componentId) {
        int separator = componentId.indexOf('_');
        return separator >= 0 ? componentId.substring(separator + 1) : componentId;
    }

    private String persist(String processId, String xml) {
        BpmProcess process = processDefinitionDao.findById(processId).orElseThrow(
                () -> new IllegalArgumentException("流程不存在: " + processId));
        process.setBpmnXml(xml);
        processDefinitionService.syncBpmnIdentity(process);
        processDefinitionDao.updateSelective(process);
        return process.getBpmnXml();
    }

    public record ApplyResult(boolean ok, String xml, String error, List<String> issues) {
        static ApplyResult ok(String xml, List<String> issues) {
            return new ApplyResult(true, xml, null, issues);
        }

        static ApplyResult fail(String error) {
            return new ApplyResult(false, null, error, List.of());
        }

        public String toToolText() {
            if (!ok) {
                return "失败，未保存。" + error;
            }
            String issueText = issues == null || issues.isEmpty()
                    ? "无校验问题"
                    : issues.stream().collect(Collectors.joining("\n- ", "校验问题:\n- ", ""));
            return "已保存到流程定义。\n" + issueText;
        }
    }
}