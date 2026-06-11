package com.kiwi.bpmn.component.s3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.bpm.engine.delegate.DelegateExecution;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class S3ObjectActivityTest {

    @Test
    void execute_missingRegion_throws() {
        DelegateExecution execution = mock(DelegateExecution.class);
        assertThrows(IllegalArgumentException.class, () -> new S3ObjectActivity().execute(execution));
    }
}
