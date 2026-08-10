package com.kiwi.project.ai.authoring.delegate;

import com.kiwi.project.ai.authoring.AiAuthoringPlanGenerateService;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("aiAuthoringGenerateDelegate")
@RequiredArgsConstructor
public class AiAuthoringGenerateDelegate implements JavaDelegate {

    private final AiAuthoringPlanGenerateService planGenerateService;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageGenerate);
        String previous = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CandidateXml);
        if (StringUtils.isBlank(previous)) {
            previous = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.BaseBpmnXml);
        }
        var result = planGenerateService.generate(
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.Scenario),
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CatalogJson),
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.IssuesJson),
                previous,
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.UserAnswer));
        execution.setVariable(AiAuthoringVariables.PlanIrJson, result.getPlanIrJson());
        execution.setVariable(AiAuthoringVariables.CandidateXml, result.getCandidateXml());
        if (result.getAssistantReply() != null) {
            execution.setVariable(AiAuthoringVariables.AssistantReply, result.getAssistantReply());
        }
    }
}
