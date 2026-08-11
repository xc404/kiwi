package com.kiwi.project.ai.assistant.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantExecutionUtils;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.AssistantWorkflowValidator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("bpmnAssistantValidateDelegate")
@RequiredArgsConstructor
public class AssistantValidateDelegate implements JavaDelegate {

    private final AssistantWorkflowValidator validator;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageValidate);
        String xml = AssistantExecutionUtils.str(execution, AssistantVariables.CandidateXml);
        String catalogJson = AssistantExecutionUtils.str(execution, AssistantVariables.CatalogJson);
        AssistantCatalog catalog = StringUtils.isBlank(catalogJson)
                ? new AssistantCatalog()
                : objectMapper.readValue(catalogJson, AssistantCatalog.class);
        var result = validator.validate(xml, catalog);
        Object roundObj = execution.getVariable(AssistantVariables.RepairRound);
        int round = roundObj instanceof Number n ? n.intValue() : 0;
        String dispatch = validator.toDispatchCode(result.getIssues(), round);
        execution.setVariable(AssistantVariables.IssuesJson, validator.issuesAsJson(result.getIssues()));
        execution.setVariable(AssistantVariables.DispatchCode, dispatch);
        if (AssistantVariables.DispatchInstall.equals(dispatch) && !result.getIssues().isEmpty()) {
            var first = result.getIssues().stream()
                    .filter(i -> "INSTALL".equals(i.getSeverity()))
                    .findFirst()
                    .orElse(result.getIssues().get(0));
            execution.setVariable(AssistantVariables.PluginHintJson,
                    objectMapper.writeValueAsString(first));
            execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageAwaitInstall);
        } else if (AssistantVariables.DispatchAsk.equals(dispatch)) {
            execution.setVariable(AssistantVariables.AskMessage,
                    result.getIssues().isEmpty() ? "需要更多信息" : result.getIssues().get(0).getMessage());
            execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageAwaitAsk);
        } else if (AssistantVariables.DispatchPass.equals(dispatch)) {
            execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageAwaitPreview);
        }
    }
}
