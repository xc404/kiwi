package com.kiwi.project.ai.assistant.delegate;

import com.kiwi.bpmn.assistant.AssistantExecutionUtils;
import com.kiwi.bpmn.assistant.AssistantKeywordExtractor;
import com.kiwi.bpmn.assistant.AssistantVariables;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("bpmnAssistantExtractDelegate")
@RequiredArgsConstructor
public class AssistantExtractDelegate implements JavaDelegate {

    private final AssistantKeywordExtractor keywordExtractor;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageExtract);
        String scenario = AssistantExecutionUtils.str(execution, AssistantVariables.Scenario);
        String keywordsJson = keywordExtractor.extractAsJson(scenario);
        execution.setVariable(AssistantVariables.KeywordsJson, keywordsJson);
    }
}
