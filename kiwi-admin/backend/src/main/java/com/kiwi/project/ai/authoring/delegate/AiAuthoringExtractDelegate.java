package com.kiwi.project.ai.authoring.delegate;

import com.kiwi.project.ai.authoring.AiAuthoringKeywordExtractor;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("aiAuthoringExtractDelegate")
@RequiredArgsConstructor
public class AiAuthoringExtractDelegate implements JavaDelegate {

    private final AiAuthoringKeywordExtractor keywordExtractor;

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageExtract);
        String scenario = str(execution, AiAuthoringVariables.Scenario);
        List<String> kws = keywordExtractor.extract(scenario);
        execution.setVariable(AiAuthoringVariables.KeywordsJson, keywordExtractor.extractAsJson(scenario));
        execution.setVariable("keywordsCount", kws.size());
    }

    public static String str(DelegateExecution execution, String name) {
        Object v = execution.getVariable(name);
        return v == null ? null : String.valueOf(v);
    }
}
