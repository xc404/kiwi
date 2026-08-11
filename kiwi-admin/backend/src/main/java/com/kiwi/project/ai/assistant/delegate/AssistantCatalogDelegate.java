package com.kiwi.project.ai.assistant.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantExecutionUtils;
import com.kiwi.bpmn.assistant.AssistantKeywordExtractor;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.project.ai.assistant.AssistantCatalogContextBuilder;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("bpmnAssistantCatalogDelegate")
@RequiredArgsConstructor
public class AssistantCatalogDelegate implements JavaDelegate {

    private final AssistantCatalogContextBuilder catalogContextBuilder;
    private final AssistantKeywordExtractor keywordExtractor;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageCatalog);
        String scenario = AssistantExecutionUtils.str(execution, AssistantVariables.Scenario);
        String keywordsJson = AssistantExecutionUtils.str(execution, AssistantVariables.KeywordsJson);
        List<String> kws = keywordsJson != null
                ? objectMapper.readValue(keywordsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                : keywordExtractor.extract(scenario);
        execution.setVariable(AssistantVariables.CatalogJson, catalogContextBuilder.buildAsJson(scenario, kws));
    }
}
