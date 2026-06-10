package com.kiwi.bpmn.example;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoGreetingActivityTest {

    @Test
    void execute_setsGreeting() throws Exception {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariableTyped("name")).thenReturn(Variables.stringValue("Kiwi"));

        new DemoGreetingActivity().execute(execution);

        verify(execution).setVariable(eq("greeting"), eq("Hello, Kiwi"));
    }
}
