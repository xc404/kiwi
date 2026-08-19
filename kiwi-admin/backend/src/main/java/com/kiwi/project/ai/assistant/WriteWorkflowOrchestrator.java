package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantBpmnToPlan;
import com.kiwi.bpmn.assistant.AssistantComponentIdAliases;
import com.kiwi.bpmn.assistant.AssistantKeywordExtractor;
import com.kiwi.bpmn.assistant.AssistantPlanGenerateService;
import com.kiwi.bpmn.assistant.AssistantValidationIssue;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import com.kiwi.bpmn.assistant.WriteWorkflowSession;
import com.kiwi.project.ai.assistant.spi.AdminAssistantComponentLookup;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.service.BpmRemoteMarketInstallService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AI 写工作流 Java 管线：extract → generate(MCP 发现) → validate → repair/ask/install/preview/save。
 * 不再构建 Catalog 菜单注入 prompt。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Deprecated(since = "2026-08", forRemoval = true)
public class WriteWorkflowOrchestrator {

    private static final Pattern AmbiguousTidy = Pattern.compile(
            "整理|规范|清理|优化布局|美化");
    private static final Pattern ConcreteEdit = Pattern.compile(
            "删除|移除|去掉|添加|追加|插入|替换|改成|换成|修改参数|改参数|连接|部署|导出|保存|复制");
    private static final Pattern ExplicitReplace = Pattern.compile(
            "替换|改成|换成|更换组件|换组件");

    private final AssistantKeywordExtractor keywordExtractor;
    private final AssistantPlanGenerateService planGenerateService;
    private final AssistantWorkflowValidator validator;
    private final ObjectMapper objectMapper;
    private final BpmRemoteMarketInstallService installService;
    private final ObjectProvider<BpmRemoteMarketService> remoteMarketServiceProvider;
    private final BpmProcessDefinitionDao processDao;
    private final BpmProcessDefinitionService processDefinitionService;
    private final AssistantBpmnToPlan bpmnToPlan;

    /**
     * 新一轮改图：抽词到校验（含 repair 循环），停在人机阶段或 done。
     */
    public WriteWorkflowSession runTurn(WriteWorkflowSession session) {
        if (isAmbiguousTidy(session.getScenario())) {
            session.setStage(AssistantVariables.StageAwaitAsk);
            session.setDispatchCode(AssistantVariables.DispatchAsk);
            session.setAskMessage(
                    "你是想「仅整理布局/命名（保留现有命令行等业务组件）」还是「调整业务步骤」？"
                            + "若要换组件请写明目标组件；若只整理布局请回复「仅整理布局」。");
            session.setAssistantReply("在改图前需要确认整理范围，以免误换业务组件。");
            return session;
        }
        extract(session);
        generate(session);
        if (applyPreserveAndSummaryGrounding(session)) {
            session.setStage(AssistantVariables.StageAwaitAsk);
            return session;
        }
        return validateLoop(session);
    }

    /**
     * 用户回答追问后，从 generate 续跑。
     */
    public WriteWorkflowSession continueAfterAsk(WriteWorkflowSession session, String userAnswer) {
        session.setUserAnswer(userAnswer);
        generate(session);
        if (applyPreserveAndSummaryGrounding(session)) {
            session.setStage(AssistantVariables.StageAwaitAsk);
            return session;
        }
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
        if (applyPreserveAndSummaryGrounding(session)) {
            session.setStage(AssistantVariables.StageAwaitAsk);
            return session;
        }
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
        if (applyPreserveAndSummaryGrounding(session)) {
            session.setStage(AssistantVariables.StageAwaitAsk);
            return session;
        }
        return validateLoop(session);
    }

    private WriteWorkflowSession validateLoop(WriteWorkflowSession session) {
        while (true) {
            validate(session);
            String dispatch = session.getDispatchCode();
            if (AssistantVariables.DispatchRepair.equals(dispatch)) {
                repair(session);
                if (applyPreserveAndSummaryGrounding(session)) {
                    session.setStage(AssistantVariables.StageAwaitAsk);
                    return session;
                }
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
            session.setStage(AssistantVariables.StageAwaitAsk);
            return session;
        }
    }

    private void extract(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageExtract);
        session.setKeywordsJson(keywordExtractor.extractAsJson(session.getScenario()));
    }

    private void generate(WriteWorkflowSession session) {
        session.setStage(AssistantVariables.StageGenerate);
        String previous = session.getCandidateXml();
        if (StringUtils.isBlank(previous)) {
            previous = session.getBaseBpmnXml();
        }
        var result = planGenerateService.generate(
                session.getScenario(),
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
            var result = validator.validate(session.getCandidateXml());
            List<AssistantValidationIssue> issues = new ArrayList<>(result.getIssues());
            boolean allowedReplace = allowsExplicitReplace(session);
            validator.appendUnauthorizedComponentSwaps(
                    session.getBaseBpmnXml(),
                    session.getCandidateXml(),
                    allowedReplace,
                    issues);
            String dispatch = validator.toDispatchCode(issues, session.getRepairRound());
            session.setIssuesJson(validator.issuesAsJson(issues));
            session.setDispatchCode(dispatch);
            if (AssistantVariables.DispatchInstall.equals(dispatch) && !issues.isEmpty()) {
                var first = issues.stream()
                        .filter(i -> "INSTALL".equals(i.getSeverity()))
                        .findFirst()
                        .orElse(issues.get(0));
                session.setPluginHintJson(objectMapper.writeValueAsString(first));
            } else if (AssistantVariables.DispatchAsk.equals(dispatch)) {
                session.setAskMessage(issues.isEmpty()
                        ? "需要更多信息"
                        : issues.get(0).getMessage());
                if (issues.stream().anyMatch(i ->
                        AssistantWorkflowValidator.CodeComponentIdChanged.equals(i.getCode()))) {
                    session.setAssistantReply(
                            "检测到原有业务组件被擅自更换。已阻止该改动进入预览保存。"
                                    + "请确认是仅整理布局，还是明确要替换成哪个组件。");
                }
            }
        } catch (Exception e) {
            log.error("validate failed", e);
            session.setDispatchCode(AssistantVariables.DispatchAsk);
            session.setAskMessage("校验失败：" + e.getMessage());
            session.setIssuesJson("[]");
        }
    }

    /**
     * @return true 若检测到擅自换组件并已回退到原图、进入追问
     */
    private boolean applyPreserveAndSummaryGrounding(WriteWorkflowSession session) {
        if (allowsExplicitReplace(session)) {
            return false;
        }
        List<AssistantValidationIssue> probe = new ArrayList<>();
        validator.appendUnauthorizedComponentSwaps(
                session.getBaseBpmnXml(),
                session.getCandidateXml(),
                false,
                probe);
        if (probe.isEmpty()) {
            return false;
        }
        // 回退到原图，避免错误候选进入画布
        if (StringUtils.isNotBlank(session.getBaseBpmnXml())) {
            session.setCandidateXml(session.getBaseBpmnXml());
            bpmnToPlan.parse(session.getBaseBpmnXml()).ifPresent(plan -> {
                try {
                    session.setPlanIrJson(objectMapper.writeValueAsString(plan));
                } catch (Exception ignored) {
                    // keep previous planIr
                }
            });
        }
        session.setAssistantReply(
                "检测到原有业务组件被擅自更换，已保留当前画布内容。"
                        + "请说明是仅整理布局/命名，还是要替换成哪个具体组件。");
        session.setAskMessage(probe.get(0).getMessage());
        session.setDispatchCode(AssistantVariables.DispatchAsk);
        try {
            session.setIssuesJson(validator.issuesAsJson(probe));
        } catch (Exception ignored) {
            session.setIssuesJson("[]");
        }
        return true;
    }

    private void install(WriteWorkflowSession session) {
        if (!Boolean.TRUE.equals(session.getInstallAccepted())) {
            throw new IllegalStateException("未确认安装插件");
        }
        session.setStage(AssistantVariables.StageInstall);
        try {
            String issueJson = session.getPluginHintJson();
            if (StringUtils.isBlank(issueJson)) {
                throw new IllegalArgumentException("缺少插件安装上下文");
            }
            AssistantValidationIssue issue = objectMapper.readValue(issueJson, AssistantValidationIssue.class);
            String hint = issue.getPluginHint();
            var market = AdminAssistantComponentLookup.parseMarketHint(hint);
            if (market.isEmpty()) {
                market = resolveMarketHintByComponentId(
                        StringUtils.defaultIfBlank(issue.getComponentId(), hint));
            }
            if (market.isEmpty()) {
                throw new IllegalArgumentException(
                        "缺少市场 slug/version，无法安装: "
                                + StringUtils.defaultIfBlank(hint, issue.getComponentId()));
            }
            var m = market.get();
            installService.installPlugin(m.slug(), m.version(), m.sourceId(), session.getInitiatorUserId());
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

    private java.util.Optional<AdminAssistantComponentLookup.MarketHint> resolveMarketHintByComponentId(
            String componentId) {
        if (StringUtils.isBlank(componentId)) {
            return java.util.Optional.empty();
        }
        BpmRemoteMarketService remote = remoteMarketServiceProvider.getIfAvailable();
        if (remote == null || !remote.isEnabled()) {
            return java.util.Optional.empty();
        }
        String bean = AssistantComponentIdAliases.beanName(componentId);
        List<BpmRemoteMarketItemDto> plugins = new ArrayList<>();
        plugins.addAll(remote.listItems("plugin", bean, null));
        if (plugins.isEmpty()) {
            plugins.addAll(remote.listItems("plugin", null, null));
        }
        for (BpmRemoteMarketItemDto item : plugins) {
            if (item == null) {
                continue;
            }
            boolean match = false;
            if (item.getComponentKeys() != null) {
                for (String key : item.getComponentKeys()) {
                    if (AssistantComponentIdAliases.sameComponent(componentId, key)
                            || componentId.equals(key)) {
                        match = true;
                        break;
                    }
                }
            }
            String slug = StringUtils.defaultString(item.getSlug()).toLowerCase(Locale.ROOT);
            if (!match && StringUtils.isNotBlank(bean) && slug.contains(bean.toLowerCase(Locale.ROOT))) {
                match = true;
            }
            if (match && StringUtils.isNoneBlank(item.getSlug(), item.getVersion())) {
                return java.util.Optional.of(new AdminAssistantComponentLookup.MarketHint(
                        item.getSlug(), item.getVersion(), item.getSourceId(), componentId));
            }
        }
        return java.util.Optional.empty();
    }

    static boolean isAmbiguousTidy(String scenario) {
        if (StringUtils.isBlank(scenario)) {
            return false;
        }
        String s = scenario.trim();
        if (!AmbiguousTidy.matcher(s).find()) {
            return false;
        }
        return !ConcreteEdit.matcher(s).find();
    }

    private boolean allowsExplicitReplace(WriteWorkflowSession session) {
        String text = (StringUtils.defaultString(session.getScenario()) + " "
                + StringUtils.defaultString(session.getUserAnswer())).toLowerCase(Locale.ROOT);
        return ExplicitReplace.matcher(text).find();
    }
}
