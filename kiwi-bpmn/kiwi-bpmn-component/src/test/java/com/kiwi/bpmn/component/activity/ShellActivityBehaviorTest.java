package com.kiwi.bpmn.component.activity;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShellActivityBehaviorTest {

    @Test
    void convertStreamToStr_readsUtf8() {
        String text = ShellActivityBehavior.convertStreamToStr(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        assertEquals("hello", text);
    }

    @Test
    void execute_echo_writesResultAndZeroExitCode() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariableTyped("command")).thenReturn(Variables.stringValue("echo kiwi-shell-test"));
        when(execution.getVariableTyped("waitFlag")).thenReturn(null);
        when(execution.getVariableTyped("redirectError")).thenReturn(null);
        when(execution.getVariableTyped("cleanEnv")).thenReturn(null);
        when(execution.getVariableTyped("directory")).thenReturn(null);

        new ShellActivityBehavior().execute(execution);

        verify(execution).setVariable(eq("errorCode"), eq("0"));
        verify(execution)
                .setVariable(
                        eq("result"),
                        org.mockito.ArgumentMatchers.argThat(
                                (Object value) ->
                                        value != null
                                                && value.toString().toLowerCase().contains("kiwi-shell-test")));
    }

    @Test
    void execute_missingCommand_throws() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariableTyped("command")).thenReturn(null);

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ShellActivityBehavior().execute(execution));
        assertTrue(ex.getMessage().contains("command"));
    }
}
