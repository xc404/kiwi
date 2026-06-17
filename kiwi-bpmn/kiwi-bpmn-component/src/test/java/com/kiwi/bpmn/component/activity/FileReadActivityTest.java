package com.kiwi.bpmn.component.activity;

import org.operaton.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.operaton.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

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
class FileReadActivityTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectUnsafePath_blocksParentSegment() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> FileReadActivity.rejectUnsafePath("../etc/passwd"));
        assertTrue(ex.getMessage().contains(".."));
    }

    @Test
    void execute_readsUtf8Content(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sample.txt");
        Files.writeString(file, "你好 kiwi");

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path")).thenReturn(Variables.stringValue(file.toString()));
        when(execution.getVariableTyped("encoding")).thenReturn(null);
        when(execution.getVariableTyped("maxBytes")).thenReturn(null);
        when(execution.getVariableTyped("content")).thenReturn(Variables.stringValue("fileContent"));

        FileReadActivity activity = spy(new FileReadActivity());
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        verify(execution).setVariable(eq("fileContent"), eq("你好 kiwi"));
        verify(activity).leave(execution);
    }

    @Test
    void execute_exceedsMaxBytes_throws(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("large.txt");
        Files.writeString(file, "1234567890");

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path")).thenReturn(Variables.stringValue(file.toString()));
        when(execution.getVariableTyped("encoding")).thenReturn(null);
        when(execution.getVariableTyped("maxBytes")).thenReturn(Variables.integerValue(5));

        FileReadActivity activity = new FileReadActivity();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> activity.execute(execution));
        assertTrue(ex.getMessage().contains("maxBytes"));
    }

    @Test
    void execute_missingFile_throws() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path"))
                .thenReturn(Variables.stringValue(tempDir.resolve("missing.txt").toString()));

        FileReadActivity activity = new FileReadActivity();
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> activity.execute(execution));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void execute_blankPath_throws() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("path")).thenReturn(Variables.stringValue("   "));

        FileReadActivity activity = new FileReadActivity();
        assertThrows(IllegalArgumentException.class, () -> activity.execute(execution));
    }
}
