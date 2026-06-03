package com.kiwi.bpmn.component.json;

import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JsonMapActivityTest {

    @Test
    void execute_resolvesSourceByVariableName() throws Exception {
        JsonMapActivity activity = spy(new JsonMapActivity(new JsonMapExecutor()));
        doNothing().when(activity).leave(org.mockito.ArgumentMatchers.any());

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("source")).thenReturn(Variables.stringValue("payload"));
        when(execution.getVariableTyped("mappings")).thenReturn(
                Variables.stringValue("[{\"key\":\"task_id\",\"value\":\"/data/id\"}]"));
        when(execution.getVariable("payload")).thenReturn("{\"data\":{\"id\":\"xyz\"}}");

        activity.execute(execution);

        verify(execution).setVariable(eq("task_id"), eq("xyz"));
        verify(activity).leave(execution);
    }
}
