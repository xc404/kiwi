package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantKeywordExtractor;
import com.kiwi.bpmn.assistant.AssistantPlanGenerateService;
import com.kiwi.bpmn.assistant.AssistantValidationIssue;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.assistant.WriteWorkflowSession;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 写工作流 Java 管线：extract → catalog → generate → validate → repair/ask/install/preview/save。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WriteWorkflowOrchestrator {

    private final AssistantKeywordExtractor keywordExtractor;
    private final AssistantCatalogContextBuilder catalogContextBuilder;
    private final AssistantPlanGenerateService planGenerateService;
    private final AssistantWorkflowValidator validator;
    private final ObjectMapper objectMapper;
    private final BpmRemoteMarketInstallService installService;
    private final BpmProcessDefinitionDao processDao;
    private final BpmProcessDefinitionService processDefinitionService;

    /**
     * 新一轮改图：抽词到校验（含 repair 循环），停在人机阶段或 done。
     */
    public WriteWorkflowSession runTurn(WriteWorkflowSession session) {
        extract(session);
        catalog(session);
        generate(session);
        return validateLoop(session);
    }

    /**
     * 用户回答追问后，从 generate 续跑。
     */
    public WriteWorkflowSession continueAfterAsk(WriteWorkflowSession session, String userAnswer) {
        session.setUserAnswer(userAnswer);
        generate(session);
        return validateLoop(session);
    }

    /**
     * 预览确认或拒绝。
     */
    public WriteWorkflowSession confirmPreview(WriteWorkflowSession session, boolean confirmed) {
        session.setPreviewConfirmed(confirmed);
        if (confirmed) {
            save(session);
            return session;
        }
        session.setUserAnswer(StringUtils.defaultIfBlank(session.getUserAnswer(), "用户拒绝了上一版预览，请按原场景重新生成"));
        generate(session);
        return validateLoop(session);
    }

    /**
     * 安装确认或拒绝。
     */
    public WriteWorkflowSession confirmInstall(WriteWorkflowSession session, boolean accepted) {
        session.setInstallAccepted(accepted);
        if (accepted) {
            install(session);
            return validateLoop(session);
        }
        session.setPluginHintJson(null);
        session.setUserAnswer(StringUtils.defaultIfBlank(
                session.getUserAnswer(), "用户拒绝安装插件，请用不依赖该插件的方式重新生成"));
        generate(session);
        return validateLoop(session);
    }

    private WriteWorkflowSession validateLoop(WriteWorkflowSession session) {
        while (true) {
            validate(session);
            String dispatch = session.getDispatchCode();
            if (AssistantVariables.DispatchRepair.equals(dispatch)) {
                repair(session);
                continue;
            }
            if (AssistantVariables.DispatchPass.equals(dispatch)) {
                session.setStage(AssistantVariables.StageAwaitPreview);
                return session;
            }
            if (AssistantVariables.DispatchInstall.equals(dispatch)) {
                session.setStage(AssistantVariables.StageAwaitInstall);
                return session;
            }
            // ASK or fallback
            session.setStage(AssistantVariables.StageAwaitAsk);
            return session;
        }
    }

    private void extract(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageExtract);
        session.setKeywordsJson(keywordExtractor.extractAsJson(session.getScenario()));
    }

    private void catalog(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageCatalog);
        List<String> kws;
        try {
            String keywordsJson = session.getKeywordsJson();
            kws = StringUtils.isNotBlank(keywordsJson)
                    ? objectMapper.readValue(keywordsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                    : keywordExtractor.extract(session.getScenario());
        } catch (Exception e) {
            kws = keywordExtractor.extract(session.getScenario());
        }
        session.setCatalogJson(catalogContextBuilder.buildAsJson(session.getScenario(), kws));
    }

    private void generate(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageGenerate);
        String previous = session.getCandidateXml();
        if (StringUtils.isBlank(previous)) {
            previous = session.getBaseBpmnXml();
        }
        var result = planGenerateService.generate(
                session.getScenario(),
                session.getCatalogJson(),
                session.getIssuesJson(),
                previous,
                session.getUserAnswer());
        session.setPlanIrJson(result.getPlanIrJson());
        session.setCandidateXml(result.getCandidateXml());
        if (result.getAssistantReply() != null) {
            session.setAssistantReply(result.getAssistantReply());
        }
    }

    private void repair(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageRepair);
        session.setRepairRound(session.getRepairRound() + 1);
        String previous = session.getCandidateXml();
        if (StringUtils.isBlank(previous)) {
            previous = session.getBaseBpmnXml();
        }
        var result = planGenerateService.generate(
                session.getScenario(),
                session.getCatalogJson(),
                session.getIssuesJson(),
                previous,
                session.getUserAnswer());
        session.setPlanIrJson(result.getPlanIrJson());
        session.setCandidateXml(result.getCandidateXml());
        if (result.getAssistantReply() != null) {
            session.setAssistantReply(result.getAssistantReply());
        }
    }

    private void validate(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageValidate);
        try {
            String catalogJson = session.getCatalogJson();
            AssistantCatalog catalog = StringUtils.isBlank(catalogJson)
                    ? new AssistantCatalog()
                    : objectMapper.readValue(catalogJson, AssistantCatalog.class);
            var result = validator.validate(session.getCandidateXml(), catalog);
            String dispatch = validator.toDispatchCode(result.getIssues(), session.getRepairRound());
            session.setIssuesJson(validator.issuesAsJson(result.getIssues()));
            session.setDispatchCode(dispatch);
            if (AssistantVariables.DispatchInstall.equals(dispatch) && !result.getIssues().isEmpty()) {
                var first = result.getIssues().stream()
                        .filter(i -> "INSTALL".equals(i.getSeverity()))
                        .findFirst()
                        .orElse(result.getIssues().get(0));
                session.setPluginHintJson(objectMapper.writeValueAsString(first));
            } else if (AssistantVariables.DispatchAsk.equals(dispatch)) {
                session.setAskMessage(result.getIssues().isEmpty()
                        ? "需要更多信息"
                        : result.getIssues().get(0).getMessage());
            }
        } catch (Exception e) {
            log.error("validate failed", e);
            session.setDispatchCode(AssistantVariables.DispatchAsk);
            session.setAskMessage("校验失败：" + e.getMessage());
            session.setIssuesJson("[]");
        }
    }

    private void install(WriteWorkflowSession session) {
        if (!Boolean.TRUE.equals(session.getInstallAccepted())) {
            throw new IllegalStateException("未确认安装插件");
        }
        session.setStage(AssistantVariables.StageInstall);
        try {
            String issueJson = session.getPluginHintJson();
            String catalogJson = session.getCatalogJson();
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
            var result = installService.installPlugin(
                    component.getMarketSlug(),
                    component.getMarketVersion(),
                    component.getMarketSourceId(),
                    session.getInitiatorUserId());
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
            session.setCatalogJson(objectMapper.writeValueAsString(catalog));
            session.setPluginHintJson(null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("安装插件失败: " + e.getMessage(), e);
        }
    }

    private void save(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageSave);
        String targetId = session.getTargetProcessId();
        String xml = session.getCandidateXml();
        if (StringUtils.isBlank(targetId) || StringUtils.isBlank(xml)) {
            session.setErrorMessage("targetProcessId 或 candidateXml 为空");
            return;
        }
        BpmProcess process = processDao.findById(targetId)
                .orElseThrow(() -> new IllegalStateException("目标流程不存在: " + targetId));
        process.setBpmnXml(xml);
        processDefinitionService.syncBpmnIdentity(process);
        process.setUpdatedTime(new Date());
        processDao.save(process);
        session.setStage(AssistantVariables.StageDone);
        session.setActive(false);
    }
}
