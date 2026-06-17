package com.kiwi.bpmn.component.activity;

import org.operaton.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.operaton.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileWriteActivityTest {

    @Test
    void execute_writesAndReportsBytes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("out.txt");

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path")).thenReturn(Variables.stringValue(file.toString()));
        when(execution.getVariableTyped("content")).thenReturn(Variables.stringValue("payload"));
        when(execution.getVariableTyped("encoding")).thenReturn(null);
        when(execution.getVariableTyped("append")).thenReturn(null);
        when(execution.getVariableTyped("createDirectories")).thenReturn(null);
        when(execution.getVariableTyped("bytesWritten")).thenReturn(Variables.stringValue("written"));

        FileWriteActivity activity = spy(new FileWriteActivity());
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        assertEquals("payload", Files.readString(file));
        verify(execution).setVariable(eq("written"), eq("payload".getBytes(StandardCharsets.UTF_8).length));
        verify(activity).leave(execution);
    }

    @Test
    void execute_appendMode(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("append.txt");
        Files.writeString(file, "a");

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path")).thenReturn(Variables.stringValue(file.toString()));
        when(execution.getVariableTyped("content")).thenReturn(Variables.stringValue("b"));
        when(execution.getVariableTyped("encoding")).thenReturn(null);
        when(execution.getVariableTyped("append")).thenReturn(Variables.booleanValue(true));
        when(execution.getVariableTyped("createDirectories")).thenReturn(null);
        when(execution.getVariableTyped("bytesWritten")).thenReturn(null);

        FileWriteActivity activity = spy(new FileWriteActivity());
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        assertEquals("ab", Files.readString(file));
    }

    @Test
    void execute_createDirectories(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nested/sub/out.txt");

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path")).thenReturn(Variables.stringValue(file.toString()));
        when(execution.getVariableTyped("content")).thenReturn(Variables.stringValue("x"));
        when(execution.getVariableTyped("encoding")).thenReturn(null);
        when(execution.getVariableTyped("append")).thenReturn(null);
        when(execution.getVariableTyped("createDirectories")).thenReturn(Variables.booleanValue(true));
        when(execution.getVariableTyped("bytesWritten")).thenReturn(null);

        FileWriteActivity activity = spy(new FileWriteActivity());
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        assertEquals("x", Files.readString(file));
    }

    @Test
    void execute_unsafePath_throws() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path")).thenReturn(Variables.stringValue("../escape.txt"));

        FileWriteActivity activity = new FileWriteActivity();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> activity.execute(execution));
        assertTrue(ex.getMessage().contains(".."));
    }
}
