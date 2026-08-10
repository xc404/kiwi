package com.kiwi.project.ai.authoring.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.authoring.AiAuthoringCatalog;
import com.kiwi.project.ai.authoring.AiAuthoringValidationIssue;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
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
@Component("aiAuthoringInstallDelegate")
@RequiredArgsConstructor
public class AiAuthoringInstallDelegate implements JavaDelegate {

    private final BpmRemoteMarketInstallService installService;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        if (!Boolean.TRUE.equals(execution.getVariable(AiAuthoringVariables.InstallAccepted))) {
            throw new IllegalStateException("未确认安装插件");
        }
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageInstall);
        String issueJson = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.PluginHintJson);
        String catalogJson = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CatalogJson);
        if (StringUtils.isBlank(issueJson) || StringUtils.isBlank(catalogJson)) {
            throw new IllegalArgumentException("缺少插件安装上下文");
        }
        AiAuthoringValidationIssue issue = objectMapper.readValue(issueJson, AiAuthoringValidationIssue.class);
        AiAuthoringCatalog catalog = objectMapper.readValue(catalogJson, AiAuthoringCatalog.class);
        AiAuthoringCatalog.CatalogComponent component = catalog.getInstallable().stream()
                .filter(candidate -> issue.getComponentId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Catalog 中不存在待安装组件: " + issue.getComponentId()));
        if (StringUtils.isBlank(component.getMarketSlug())
                || StringUtils.isBlank(component.getMarketVersion())) {
            throw new IllegalArgumentException("待安装组件缺少市场 slug/version: " + component.getId());
        }
        String userId = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.InitiatorUserId);
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
        execution.setVariable(AiAuthoringVariables.CatalogJson, objectMapper.writeValueAsString(catalog));
        execution.setVariable(AiAuthoringVariables.PluginHintJson, null);
    }
}
