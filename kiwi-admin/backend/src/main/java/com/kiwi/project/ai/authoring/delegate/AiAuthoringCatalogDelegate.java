package com.kiwi.project.ai.authoring.delegate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.project.ai.authoring.AiAuthoringCatalogContextBuilder;
import com.kiwi.project.ai.authoring.AiAuthoringKeywordExtractor;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("aiAuthoringCatalogDelegate")
@RequiredArgsConstructor
public class AiAuthoringCatalogDelegate implements JavaDelegate {

    private final AiAuthoringCatalogContextBuilder catalogContextBuilder;
    private final AiAuthoringKeywordExtractor keywordExtractor;
    private final ObjectMapper objectMapper;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageCatalog);
        String scenario = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.Scenario);
        String keywordsJson = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.KeywordsJson);
        List<String> kws = keywordsJson != null
                ? objectMapper.readValue(keywordsJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class))
                : keywordExtractor.extract(scenario);
        execution.setVariable(AiAuthoringVariables.CatalogJson, catalogContextBuilder.buildAsJson(scenario, kws));
    }
}
