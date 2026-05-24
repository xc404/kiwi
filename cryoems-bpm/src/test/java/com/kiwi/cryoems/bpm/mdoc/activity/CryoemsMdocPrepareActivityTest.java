package com.kiwi.cryoems.bpm.mdoc.activity;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CryoemsMdocPrepareActivityTest {

    private CryoemsMdocPrepareActivity activity;

    @BeforeEach
    void setUp() {
        activity = new CryoemsMdocPrepareActivity();
    }

    @Test
    void execute_createsMdocSubdirAndExposesAbsolutePath(@TempDir File workRoot) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("work_dir", workRoot.getAbsolutePath());
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        Path expected = workRoot.toPath().resolve("mdoc");
        assertThat(Files.isDirectory(expected)).isTrue();
        assertThat(vars.get("mdocWorkDir")).isEqualTo(expected.toAbsolutePath().toString());
    }

    @Test
    void execute_isIdempotentWhenSubdirExists(@TempDir File workRoot) throws Exception {
        Path existing = workRoot.toPath().resolve("mdoc");
        Files.createDirectories(existing);
        Path sentinel = Files.writeString(existing.resolve("keep.txt"), "x");

        Map<String, Object> vars = new HashMap<>();
        vars.put("work_dir", workRoot.getAbsolutePath());
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(Files.isDirectory(existing)).isTrue();
        assertThat(Files.exists(sentinel)).isTrue();
        assertThat(vars.get("mdocWorkDir")).isEqualTo(existing.toAbsolutePath().toString());
    }

    @Test
    void execute_readsWorkDirFromTaskMapWhenTopLevelMissing(@TempDir File workRoot) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("task", Map.of("work_dir", workRoot.getAbsolutePath()));
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        Path expected = workRoot.toPath().resolve("mdoc");
        assertThat(Files.isDirectory(expected)).isTrue();
        assertThat(vars.get("mdocWorkDir")).isEqualTo(expected.toAbsolutePath().toString());
    }

    @Test
    void execute_throwsBpmnErrorWhenWorkDirMissing() {
        Map<String, Object> vars = new HashMap<>();
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("work_dir");
    }

    private static DelegateExecution stubExecution(Map<String, Object> vars) {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable(anyString())).thenAnswer(inv -> vars.get((String) inv.getArgument(0)));
        when(execution.getVariableTyped(anyString())).thenAnswer(inv -> {
            Object value = vars.get((String) inv.getArgument(0));
            if (value == null) {
                return null;
            }
            return Variables.objectValue(value).create();
        });
        doAnswer(inv -> {
            vars.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(execution).setVariable(anyString(), any());
        return execution;
    }
}
