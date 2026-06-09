package com.kiwi.bpmn.component.utils;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.variable.Variables;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionUtilsTest {

    @Test
    void getStringInputVariableAtPath_readsNestedMap() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariableTyped("task")).thenReturn(
                Variables.objectValue(Map.of("config_id", "cfg-1")));
        when(execution.getVariableTyped("movie")).thenReturn(
                Variables.objectValue(Map.of("id", "m-1", "data_id", "d-1")));

        assertEquals("cfg-1", ExecutionUtils.getStringInputVariableAtPath(execution, "task.config_id").orElseThrow());
        assertEquals("m-1", ExecutionUtils.getStringInputVariableAtPath(execution, "movie.id").orElseThrow());
        assertEquals("d-1", ExecutionUtils.getStringInputVariableAtPath(execution, "movie.data_id").orElseThrow());
    }

    @Test
    void getStringInputVariableAtPath_flatVariableWithoutDot() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariableTyped("motionNoDwMrc")).thenReturn(
                Variables.stringValue("/path/out.mrc"));

        assertEquals("/path/out.mrc",
                ExecutionUtils.getStringInputVariableAtPath(execution, "motionNoDwMrc").orElseThrow());
    }

    @Test
    void getStringInputVariableAtPath_fallsBackToLiteralDottedName() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariableTyped("task")).thenReturn(null);
        when(execution.getVariableTyped("task.config_id")).thenReturn(Variables.stringValue("literal"));

        assertEquals("literal", ExecutionUtils.getStringInputVariableAtPath(execution, "task.config_id").orElseThrow());
    }

    @Test
    void getStringInputVariableAtPath_missingPathIsEmpty() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariableTyped("task")).thenReturn(Variables.objectValue(Map.of()));

        assertTrue(ExecutionUtils.getStringInputVariableAtPath(execution, "task.config_id").isEmpty());
    }
}
