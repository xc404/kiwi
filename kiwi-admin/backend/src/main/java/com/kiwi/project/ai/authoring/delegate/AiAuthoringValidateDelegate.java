package com.kiwi.project.ai.authoring.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.authoring.AiAuthoringCatalog;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import com.kiwi.project.ai.authoring.BpmAiWorkflowValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("aiAuthoringValidateDelegate")
@RequiredArgsConstructor
public class AiAuthoringValidateDelegate implements JavaDelegate {

    private final BpmAiWorkflowValidator validator;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageValidate);
        String xml = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CandidateXml);
        String catalogJson = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CatalogJson);
        AiAuthoringCatalog catalog = StringUtils.isBlank(catalogJson)
                ? new AiAuthoringCatalog()
                : objectMapper.readValue(catalogJson, AiAuthoringCatalog.class);
        var result = validator.validate(xml, catalog);
        Object roundObj = execution.getVariable(AiAuthoringVariables.RepairRound);
        int round = roundObj instanceof Number n ? n.intValue() : 0;
        String dispatch = validator.toDispatchCode(result.getIssues(), round);
        execution.setVariable(AiAuthoringVariables.IssuesJson, validator.issuesAsJson(result.getIssues()));
        execution.setVariable(AiAuthoringVariables.DispatchCode, dispatch);
        if (AiAuthoringVariables.DispatchInstall.equals(dispatch) && !result.getIssues().isEmpty()) {
            var first = result.getIssues().stream()
                    .filter(i -> "INSTALL".equals(i.getSeverity()))
                    .findFirst()
                    .orElse(result.getIssues().get(0));
            execution.setVariable(AiAuthoringVariables.PluginHintJson,
                    objectMapper.writeValueAsString(first));
            execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageAwaitInstall);
        } else if (AiAuthoringVariables.DispatchAsk.equals(dispatch)) {
            execution.setVariable(AiAuthoringVariables.AskMessage,
                    result.getIssues().isEmpty() ? "需要更多信息" : result.getIssues().get(0).getMessage());
            execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageAwaitAsk);
        } else if (AiAuthoringVariables.DispatchPass.equals(dispatch)) {
            execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageAwaitPreview);
        }
    }
}
