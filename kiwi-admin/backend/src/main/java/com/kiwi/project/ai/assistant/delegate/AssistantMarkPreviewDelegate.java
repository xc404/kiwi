package com.kiwi.project.ai.assistant.delegate;

import com.kiwi.bpmn.assistant.AssistantVariables;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("bpmnAssistantMarkPreviewDelegate")
public class AssistantMarkPreviewDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageAwaitPreview);
    }
}
