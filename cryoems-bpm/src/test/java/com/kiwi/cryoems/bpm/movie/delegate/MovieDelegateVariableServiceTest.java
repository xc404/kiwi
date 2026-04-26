package com.kiwi.cryoems.bpm.movie.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MovieDelegateVariableServiceTest {

    private MovieDelegateVariableService service;
    private DelegateExecution execution;

    @BeforeEach
    void setUp() {
        service = new MovieDelegateVariableService();
        execution = mock(DelegateExecution.class);
    }

    @Test
    void shouldWriteFlatDataWithoutPrefix() {
        service.writeData(execution, Map.of("movieFilePath", "/tmp/a.mrc", "header-file", "/tmp/a_meta.txt"));

        verify(execution).setVariable("movieFilePath", "/tmp/a.mrc");
        verify(execution).setVariable("header-file", "/tmp/a_meta.txt");
    }

    @Test
    void shouldWriteExceptionData() {
        RuntimeException ex = new RuntimeException("boom");

        service.writeExceptionData(execution, ex, true, false);

        verify(execution).setVariable("exceptionMessage", "boom");
        verify(execution).setVariable("exceptionType", RuntimeException.class.getName());
        verify(execution).setVariable("retryable", true);
        verify(execution).setVariable("fatal", false);
    }

    @Test
    void requiredVariableShouldThrowWhenMissing() {
        when(execution.getVariable("movie")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.requiredVariable(execution, "movie")
        );
        assertEquals("流程变量缺失: movie", ex.getMessage());
    }
}
