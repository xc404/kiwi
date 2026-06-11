package com.kiwi.bpmn.component.rabbitmq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.operaton.bpm.engine.delegate.DelegateExecution;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class RabbitMqPublishActivityTest {

    @Test
    void execute_missingHost_throws() {
        DelegateExecution execution = mock(DelegateExecution.class);
        assertThrows(IllegalArgumentException.class, () -> new RabbitMqPublishActivity().execute(execution));
    }
}
