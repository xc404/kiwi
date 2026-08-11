package com.kiwi.project.ai.assistant.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantExecutionUtils;
import com.kiwi.bpmn.assistant.AssistantValidationIssue;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 用户确认后安装候选流程所需的远程市场插件。
 */
@Component("bpmnAssistantInstallDelegate")
@RequiredArgsConstructor
public class AssistantInstallDelegate implements JavaDelegate {

    private final BpmRemoteMarketInstallService installService;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        if (!Boolean.TRUE.equals(execution.getVariable(AssistantVariables.InstallAccepted))) {
            throw new IllegalStateException("未确认安装插件");
        }
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageInstall);
        String issueJson = AssistantExecutionUtils.str(execution, AssistantVariables.PluginHintJson);
        String catalogJson = AssistantExecutionUtils.str(execution, AssistantVariables.CatalogJson);
        if (StringUtils.isBlank(issueJson) || StringUtils.isBlank(catalogJson)) {
            throw new IllegalArgumentException("缺少插件安装上下文");
        }
        AssistantValidationIssue issue = objectMapper.readValue(issueJson, AssistantValidationIssue.class);
        AssistantCatalog catalog = objectMapper.readValue(catalogJson, AssistantCatalog.class);
        AssistantCatalog.CatalogComponent component = catalog.getInstallable().stream()
                .filter(candidate -> issue.getComponentId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Catalog 中不存在待安装组件: " + issue.getComponentId()));
        if (StringUtils.isBlank(component.getMarketSlug())
                || StringUtils.isBlank(component.getMarketVersion())) {
            throw new IllegalArgumentException("待安装组件缺少市场 slug/version: " + component.getId());
        }
        String userId = AssistantExecutionUtils.str(execution, AssistantVariables.InitiatorUserId);
        var result = installService.installPlugin(
                component.getMarketSlug(),
                component.getMarketVersion(),
                component.getMarketSourceId(),
                userId);
        Set<String> installedIds = new LinkedHashSet<>();
        if (result != null && result.getInstalledComponentKeys() != null) {
            installedIds.addAll(result.getInstalledComponentKeys());
        }
        installedIds.add(issue.getComponentId());
        catalog.getInstallable().removeIf(candidate -> {
            if (!installedIds.contains(candidate.getId())) {
                return false;
            }
            candidate.setStatus("installed");
            candidate.setRequiresInstall(false);
            catalog.getInstalled().add(candidate);
            return true;
        });
        execution.setVariable(AssistantVariables.CatalogJson, objectMapper.writeValueAsString(catalog));
        execution.setVariable(AssistantVariables.PluginHintJson, null);
    }
}
