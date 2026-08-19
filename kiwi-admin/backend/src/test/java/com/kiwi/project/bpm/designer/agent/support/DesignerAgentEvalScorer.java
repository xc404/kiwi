package com.kiwi.project.bpm.designer.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantComponentIdAliases;
import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对 Agent run 结果打分。
 */
public class DesignerAgentEvalScorer {

    private static final Pattern ComponentIdPattern =
            Pattern.compile("kiwi:componentId=\"([^\"]+)\"");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DesignerAgentEvalRunResult score(
            DesignerAgentEvalCase evalCase,
            DesignerAgentEvalClient.RunSnapshot snapshot) {
        DesignerAgentEvalRunResult result = new DesignerAgentEvalRunResult();
        result.setCaseId(evalCase.getId());
        result.setScenario(evalCase.getScenario());
        result.setTargetProcessId(snapshot.targetProcessId());
        result.setRunId(snapshot.runId());
        result.setElapsedMs(snapshot.elapsedMs());
        result.setStage(snapshot.stage());
        result.setPlanSkipped(snapshot.planSkipped());
        result.setRepairRoundsObserved(snapshot.repairRoundsObserved());
        result.setSseEventTypes(snapshot.sseEventTypes());
        result.setAssistantReply(snapshot.assistantReply());
        result.setCandidateXml(snapshot.candidateXml());
        result.setEditPlanJson(snapshot.editPlanJson());
        result.setIssuesJson(snapshot.issuesJson());
        result.setErrorMessage(snapshot.errorMessage());
        result.setMaxScore(evalCase.getMaxScore());
        result.setIssues(parseIssues(snapshot.issuesJson()));
        result.setComponentIdsUsed(extractComponentIds(snapshot.candidateXml()));

        if (evalCase.isReadOnly()) {
            scoreReadOnly(evalCase, snapshot, result);
        } else {
            scoreWrite(evalCase, snapshot, result);
        }

        result.setPassed(result.getFailureReasons().isEmpty()
                && result.getScore() >= evalCase.getMinScore());
        if (StringUtils.isNotBlank(snapshot.errorMessage())) {
            result.getFailureReasons().add("error: " + snapshot.errorMessage());
            result.setPassed(false);
        }
        return result;
    }

    private void scoreReadOnly(
            DesignerAgentEvalCase evalCase,
            DesignerAgentEvalClient.RunSnapshot snapshot,
            DesignerAgentEvalRunResult result) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int score = 0;
        if (AgentRunStage.Done.equals(snapshot.stage())) {
            breakdown.put("stageDone", 3);
            score += 3;
        } else {
            result.getFailureReasons().add("只读场景应 stage=done，实际: " + snapshot.stage());
        }
        if (StringUtils.isNotBlank(snapshot.assistantReply())) {
            breakdown.put("reply", 2);
            score += 2;
        } else {
            result.getFailureReasons().add("只读场景缺少 assistantReply");
        }
        if (StringUtils.isBlank(snapshot.candidateXml()) || snapshot.candidateXml().equals(snapshot.baseBpmnXml())) {
            breakdown.put("noWrite", 2);
            score += 2;
        } else {
            result.getFailureReasons().add("只读场景不应产出新的 candidateXml");
        }
        result.setScoreBreakdown(breakdown);
        result.setScore(score);
    }

    private void scoreWrite(
            DesignerAgentEvalCase evalCase,
            DesignerAgentEvalClient.RunSnapshot snapshot,
            DesignerAgentEvalRunResult result) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int score = 0;
        String xml = StringUtils.defaultString(snapshot.candidateXml());

        for (String forbidden : evalCase.getForbiddenFragments()) {
            if (xml.contains(forbidden)) {
                result.getFailureReasons().add("出现禁止片段: " + forbidden);
            }
        }

        boolean hasStart = xml.contains("startEvent") || xml.contains("StartEvent");
        boolean hasEnd = xml.contains("endEvent") || xml.contains("EndEvent");
        if (hasStart && hasEnd) {
            breakdown.put("structure", 2);
            score += 2;
        } else {
            result.getFailureReasons().add("BPMN 缺少 start/end 事件");
        }

        if (xml.contains("BPMNDiagram")) {
            breakdown.put("diagram", 1);
            score += 1;
        }

        List<String> usedIds = result.getComponentIdsUsed();
        if (!usedIds.isEmpty()) {
            breakdown.put("components", 2);
            score += 2;
        } else if (!evalCase.isAllowPartial()) {
            result.getFailureReasons().add("未引用任何 kiwi:componentId");
        }

        if (!evalCase.getExpectedComponentIds().isEmpty()) {
            boolean componentOk = matchesExpectedComponents(
                    evalCase.getExpectedComponentIds(), usedIds, evalCase.isMatchAllComponents());
            if (componentOk) {
                breakdown.put("expectedComponents", 2);
                score += 2;
            } else {
                result.getFailureReasons().add("未命中期望组件: " + evalCase.getExpectedComponentIds()
                        + "，实际: " + usedIds);
            }
        }

        if (!evalCase.getExpectedFragments().isEmpty()) {
            int hit = 0;
            for (String fragment : evalCase.getExpectedFragments()) {
                if (xml.contains(fragment)) {
                    hit++;
                } else {
                    result.getFailureReasons().add("缺少期望片段: " + fragment);
                }
            }
            if (hit == evalCase.getExpectedFragments().size()) {
                breakdown.put("fragments", 1);
                score += 1;
            }
        }

        if (!evalCase.getKeywordHints().isEmpty()) {
            boolean keywordHit = evalCase.getKeywordHints().stream().anyMatch(hint ->
                    StringUtils.containsIgnoreCase(xml, hint)
                            || StringUtils.containsIgnoreCase(snapshot.assistantReply(), hint));
            if (keywordHit) {
                breakdown.put("keywords", 1);
                score += 1;
            } else {
                result.getFailureReasons().add("未命中语义关键词: " + evalCase.getKeywordHints());
            }
        }

        if (StringUtils.equals(evalCase.getExpectedStage(), snapshot.stage())
                || isAcceptableStage(evalCase, snapshot.stage())) {
            breakdown.put("stage", 1);
            score += 1;
        } else if (evalCase.isAllowPartial()) {
            breakdown.put("stagePartial", 0);
        } else {
            result.getFailureReasons().add("未到达期望 stage " + evalCase.getExpectedStage()
                    + "，实际: " + snapshot.stage());
        }

        if (result.getIssues().isEmpty() || onlyInfoIssues(result.getIssues())) {
            breakdown.put("validation", 1);
            score += 1;
            result.setDispatchHint("PASS");
        } else if (snapshot.repairRoundsObserved() >= evalCase.getMaxRepairRounds()) {
            result.setDispatchHint("REPAIR_EXHAUSTED");
            result.getFailureReasons().add("repair 轮数已达上限: " + snapshot.repairRoundsObserved());
        } else {
            result.setDispatchHint("ISSUES");
        }

        if (snapshot.repairRoundsObserved() == 0 && "PASS".equals(result.getDispatchHint())) {
            breakdown.put("oneShot", 1);
            score += 1;
        }

        result.setScoreBreakdown(breakdown);
        result.setScore(Math.min(score, evalCase.getMaxScore()));
    }

    private static boolean matchesExpectedComponents(
            List<String> expected, List<String> used, boolean matchAll) {
        if (expected.isEmpty()) {
            return true;
        }
        if (matchAll) {
            return expected.stream().allMatch(exp -> used.stream()
                    .anyMatch(u -> AssistantComponentIdAliases.sameComponent(exp, u)));
        }
        return expected.stream().anyMatch(exp -> used.stream()
                .anyMatch(u -> AssistantComponentIdAliases.sameComponent(exp, u)));
    }

    private static boolean isAcceptableStage(DesignerAgentEvalCase evalCase, String stage) {
        if (AgentRunStage.AwaitPreview.equals(evalCase.getExpectedStage())) {
            return AgentRunStage.AwaitPreview.equals(stage)
                    || AgentRunStage.Done.equals(stage);
        }
        return false;
    }

    private static boolean onlyInfoIssues(List<Map<String, String>> issues) {
        return issues.stream().allMatch(i -> "INFO".equalsIgnoreCase(i.get("severity")));
    }

    private List<Map<String, String>> parseIssues(String issuesJson) {
        List<Map<String, String>> list = new ArrayList<>();
        if (StringUtils.isBlank(issuesJson)) {
            return list;
        }
        try {
            JsonNode root = objectMapper.readTree(issuesJson);
            if (!root.isArray()) {
                return list;
            }
            for (JsonNode node : root) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("ruleId", text(node, "ruleId"));
                item.put("severity", text(node, "severity"));
                item.put("message", text(node, "message"));
                list.add(item);
            }
        } catch (Exception ignored) {
            Map<String, String> raw = new LinkedHashMap<>();
            raw.put("message", issuesJson);
            list.add(raw);
        }
        return list;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    public static List<String> extractComponentIds(String xml) {
        Matcher matcher = ComponentIdPattern.matcher(xml == null ? "" : xml);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
