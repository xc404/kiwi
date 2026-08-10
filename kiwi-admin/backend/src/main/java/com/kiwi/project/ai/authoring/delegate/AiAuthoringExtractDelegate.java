package com.kiwi.project.ai.authoring.delegate;

import com.kiwi.project.ai.authoring.AiAuthoringKeywordExtractor;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("aiAuthoringExtractDelegate")
@RequiredArgsConstructor
public class AiAuthoringExtractDelegate implements JavaDelegate {

    private final AiAuthoringKeywordExtractor keywordExtractor;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageExtract);
        String scenario = str(execution, AiAuthoringVariables.Scenario);
        String keywordsJson = keywordExtractor.extractAsJson(scenario);
        execution.setVariable(AiAuthoringVariables.KeywordsJson, keywordsJson);
    }

    public static String str(DelegateExecution execution, String name) {
        Object v = execution.getVariable(name);
        return v == null ? null : String.valueOf(v);
    }
}
