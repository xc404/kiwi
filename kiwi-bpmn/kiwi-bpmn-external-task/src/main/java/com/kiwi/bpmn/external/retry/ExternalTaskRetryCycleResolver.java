package com.kiwi.bpmn.external.retry;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Task;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaFailedJobRetryTimeCycle;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

import java.util.Collection;
import java.util.Optional;

/**
 * 从已部署 BPMN 中解析当前 External Task 对应活动的
 * {@code <camunda:failedJobRetryTimeCycle>}，与异步 Job 使用同一扩展元素。
 * <p>
 * 若节点未配置该元素、或文本为 JUEL 表达式且无法在 Worker 侧求值，则返回 empty，由调用方回退到全局默认。
 */
public final class ExternalTaskRetryCycleResolver {

    private final RepositoryService repositoryService;

    public ExternalTaskRetryCycleResolver(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * @return 非空且非空白且非 JUEL 表达式形态时返回 cycle 字面值
     */
    public Optional<String> resolveFromBpmn(ExternalTask task) {
        String processDefinitionId = task.getProcessDefinitionId();
        String activityId = task.getActivityId();
        if (processDefinitionId == null || activityId == null) {
            return Optional.empty();
        }
        try {
            BpmnModelInstance model = repositoryService.getBpmnModelInstance(processDefinitionId);
            if (model == null) {
                return Optional.empty();
            }
            ModelElementInstance el = model.getModelElementById(activityId);
            if (!(el instanceof Task bpmnTask)) {
                return Optional.empty();
            }
            ExtensionElements extensionElements = bpmnTask.getExtensionElements();
            if (extensionElements == null) {
                return Optional.empty();
            }
            Collection<CamundaFailedJobRetryTimeCycle> cycles =
                    extensionElements.getElementsQuery()
                            .filterByType(CamundaFailedJobRetryTimeCycle.class)
                            .list();
            if (cycles.isEmpty()) {
                return Optional.empty();
            }
            String raw = cycles.iterator().next().getTextContent();
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            String trimmed = raw.trim();
            if (looksLikeExpression(trimmed)) {
                return Optional.empty();
            }
            return Optional.of(trimmed);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static boolean looksLikeExpression(String s) {
        return s.startsWith("${") && s.endsWith("}");
    }
}
