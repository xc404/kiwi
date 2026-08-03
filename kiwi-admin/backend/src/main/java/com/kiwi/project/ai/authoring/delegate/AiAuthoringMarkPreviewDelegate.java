package com.kiwi.project.ai.authoring.delegate;

import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("aiAuthoringMarkPreviewDelegate")
public class AiAuthoringMarkPreviewDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageAwaitPreview);
    }
}
