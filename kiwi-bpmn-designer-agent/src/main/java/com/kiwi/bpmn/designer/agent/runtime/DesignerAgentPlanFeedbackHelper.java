package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

/**
 * Plan 闸门用户反馈 → 重规划 scenario 文本（纯函数工具）。
 */
public final class DesignerAgentPlanFeedbackHelper {

    private static final ObjectMapper Json = new ObjectMapper();

    private DesignerAgentPlanFeedbackHelper() {
    }

    public static String buildReplanScenario(
            String originalScenario,
            String planDisplayJson,
            String planSummary,
            String feedbackText) {
        String original = StringUtils.defaultIfBlank(originalScenario, "").trim();
        String feedback = StringUtils.defaultIfBlank(feedbackText, "").trim();
        if (StringUtils.isBlank(feedback)) {
            feedback = "用户拒绝了计划，请重新规划";
        }
        String summary = resolvePlanSummary(planDisplayJson, planSummary);
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(original)) {
            sb.append("原需求：").append(original).append('\n');
        }
        if (StringUtils.isNotBlank(summary)) {
            sb.append("上一版计划摘要：").append(summary).append('\n');
        }
        sb.append("用户修改意见：").append(feedback).append('\n');
        sb.append("请根据用户修改意见重新生成 EditPlan。");
        return sb.toString();
    }

    private static String resolvePlanSummary(String planDisplayJson, String planSummary) {
        if (StringUtils.isNotBlank(planSummary)) {
            return planSummary.trim();
        }
        if (StringUtils.isBlank(planDisplayJson)) {
            return "";
        }
        try {
            JsonNode node = Json.readTree(planDisplayJson);
            JsonNode summary = node.get("summary");
            if (summary != null && !summary.isNull()) {
                return summary.asText("").trim();
            }
        } catch (Exception ignored) {
            /* fall through */
        }
        return "";
    }
}
