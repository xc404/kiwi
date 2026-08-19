package com.kiwi.bpmn.designer.agent.apply;

import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.designer.agent.model.EditOperation;
import com.kiwi.bpmn.designer.agent.model.EditPlan;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 判定是否跳过 Plan 审阅闸门（规则，无额外 LLM）。
 */
@Component
public class PlanSkipEvaluator {

    private static final Set<String> SimpleOps = Set.of("addNode", "updateNode", "removeNode", "addFlow", "removeFlow");
    private static final Pattern ComplexIntent = Pattern.compile(
            "网关|分支|条件|重构|整流程|并行|子流程|exclusive|gateway|parallel", Pattern.CASE_INSENSITIVE);

    private final DesignerAgentProperties properties;

    public PlanSkipEvaluator(DesignerAgentProperties properties) {
        this.properties = properties;
    }

    public boolean shouldSkipPlan(EditPlan plan, String userScenario) {
        if (!properties.isPlanMode()) {
            return true;
        }
        if (!properties.isPlanModeSkipSimple()) {
            return false;
        }
        if (plan == null || plan.getOperations() == null) {
            return true;
        }
        if (plan.getOperations().size() > 2) {
            return false;
        }
        for (EditOperation op : plan.getOperations()) {
            if (op == null || !SimpleOps.contains(op.getOp())) {
                return false;
            }
        }
        if (StringUtils.isNotBlank(userScenario) && ComplexIntent.matcher(userScenario).find()) {
            return false;
        }
        return true;
    }
}
