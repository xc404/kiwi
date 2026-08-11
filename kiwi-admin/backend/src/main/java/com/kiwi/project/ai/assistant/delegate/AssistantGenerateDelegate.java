package com.kiwi.project.ai.assistant.delegate;

import com.kiwi.bpmn.assistant.AssistantExecutionUtils;
import com.kiwi.bpmn.assistant.AssistantPlanGenerateService;
import com.kiwi.bpmn.assistant.AssistantVariables;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("bpmnAssistantGenerateDelegate")
@RequiredArgsConstructor
public class AssistantGenerateDelegate implements JavaDelegate {

    private final AssistantPlanGenerateService planGenerateService;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageGenerate);
        String previous = AssistantExecutionUtils.str(execution, AssistantVariables.CandidateXml);
        if (StringUtils.isBlank(previous)) {
            previous = AssistantExecutionUtils.str(execution, AssistantVariables.BaseBpmnXml);
        }
        var result = planGenerateService.generate(
                AssistantExecutionUtils.str(execution, AssistantVariables.Scenario),
                AssistantExecutionUtils.str(execution, AssistantVariables.CatalogJson),
                AssistantExecutionUtils.str(execution, AssistantVariables.IssuesJson),
                previous,
                AssistantExecutionUtils.str(execution, AssistantVariables.UserAnswer));
        execution.setVariable(AssistantVariables.PlanIrJson, result.getPlanIrJson());
        execution.setVariable(AssistantVariables.CandidateXml, result.getCandidateXml());
        if (result.getAssistantReply() != null) {
            execution.setVariable(AssistantVariables.AssistantReply, result.getAssistantReply());
        }
    }
}
