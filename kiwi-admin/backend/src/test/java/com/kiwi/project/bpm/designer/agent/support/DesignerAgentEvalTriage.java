package com.kiwi.project.bpm.designer.agent.support;

import com.kiwi.bpmn.designer.agent.model.AgentRunStage;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 失败归因与优化建议。
 */
public class DesignerAgentEvalTriage {

    public String buildTriageReport(List<DesignerAgentEvalRunResult> results, Baseline baseline) {
        long passed = results.stream().filter(DesignerAgentEvalRunResult::isPassed).count();
        double passRate = results.isEmpty() ? 0 : (double) passed / results.size();
        double avgScore = results.stream().mapToInt(DesignerAgentEvalRunResult::getScore).average().orElse(0);

        StringBuilder md = new StringBuilder();
        md.append("# Designer Agent 评测归因\n\n");
        md.append("- 通过率: ").append(passed).append('/').append(results.size());
        if (baseline != null) {
            md.append(" (baseline minPassRate=").append(baseline.minPassRate()).append(')');
            if (passRate < baseline.minPassRate()) {
                md.append(" ❌ 回归");
            }
        }
        md.append('\n');
        md.append("- 平均分: ").append(String.format("%.1f", avgScore)).append('\n');
        md.append('\n');

        List<DesignerAgentEvalRunResult> failed = results.stream()
                .filter(r -> !r.isPassed())
                .toList();
        if (failed.isEmpty()) {
            md.append("## 结论\n\n全部用例通过，可更新 baseline。\n");
            return md.toString();
        }

        md.append("## 待优化项\n\n");
        int index = 1;
        for (DesignerAgentEvalRunResult result : failed) {
            md.append(index++).append(". **").append(result.getCaseId()).append("**");
            md.append(" (score=").append(result.getScore()).append('/').append(result.getMaxScore()).append(")\n");
            for (String reason : result.getFailureReasons()) {
                md.append("   - ").append(reason).append('\n');
            }
            for (String suggestion : suggest(result)) {
                md.append("   - 建议: ").append(suggestion).append('\n');
            }
            md.append('\n');
        }
        return md.toString();
    }

    private List<String> suggest(DesignerAgentEvalRunResult result) {
        List<String> tips = new ArrayList<>();
        for (Map<String, String> issue : result.getIssues()) {
            String ruleId = issue.get("ruleId");
            if (StringUtils.isNotBlank(ruleId)) {
                tips.add("检查 plan-ir-rules.json / Validator 规则 `" + ruleId + "`");
            }
        }
        if ("REPAIR_EXHAUSTED".equals(result.getDispatchHint())) {
            tips.add("加强 DesignerAgentPlanGenerator repair 上下文或提高 max-repair-rounds");
        }
        if (result.getComponentIdsUsed().isEmpty()) {
            tips.add("加强 MCP 组件发现约束（bpmComp_aiPage），禁止臆造 componentId");
        }
        if (result.getFailureReasons().stream().anyMatch(r -> r.contains("期望组件"))) {
            tips.add("在 prompt 中强调 fileWrite → classpath_fileWrite 映射示例");
        }
        if (AgentRunStage.AwaitAsk.equals(result.getStage())) {
            tips.add("场景信息不足，可补充 path/content 默认值或扩展 eval case scenario");
        }
        if (AgentRunStage.AwaitInstall.equals(result.getStage())) {
            tips.add("所需插件未安装，评测环境需预装组件或 allowPartial");
        }
        return tips.stream().distinct().collect(Collectors.toList());
    }

    public record Baseline(int version, double minPassRate, Map<String, CaseBaseline> cases) {
    }

    public record CaseBaseline(int minScore) {
    }
}
