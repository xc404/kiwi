package com.kiwi.project.ai.authoring.delegate;

import com.kiwi.project.ai.authoring.AiAuthoringPlanGenerateService;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("aiAuthoringRepairDelegate")
@RequiredArgsConstructor
public class AiAuthoringRepairDelegate implements JavaDelegate {

    private final AiAuthoringPlanGenerateService planGenerateService;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageRepair);
        Object roundObj = execution.getVariable(AiAuthoringVariables.RepairRound);
        int round = roundObj instanceof Number n ? n.intValue() : 0;
        execution.setVariable(AiAuthoringVariables.RepairRound, round + 1);
        var result = planGenerateService.generate(
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.Scenario),
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CatalogJson),
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.IssuesJson),
                AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CandidateXml));
        execution.setVariable(AiAuthoringVariables.PlanIrJson, result.getPlanIrJson());
        execution.setVariable(AiAuthoringVariables.CandidateXml, result.getCandidateXml());
    }
}
