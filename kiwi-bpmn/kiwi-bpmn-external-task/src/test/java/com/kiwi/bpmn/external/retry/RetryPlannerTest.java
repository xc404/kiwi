package com.kiwi.bpmn.external.retry;

import com.kiwi.bpmn.core.retry.RetryPlan;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class RetryPlannerTest
{

    @Test
    void firstFailure_r5pt1m_nextRetriesIs4() {
        ExternalTask task = Mockito.mock(ExternalTask.class);
        when(task.getRetries()).thenReturn(null);
        when(task.getErrorMessage()).thenReturn(null);

        ExternalTaskRetryPlanner planner = new ExternalTaskRetryPlanner(null, null, null);
        RetryPlan plan = planner.plan("R5/PT1M", task);

        assertEquals(4, plan.nextRetries());
    }

    @Test
    void secondFailure_retries4_nextRetriesIs3() {
        ExternalTask task = Mockito.mock(ExternalTask.class);
        when(task.getRetries()).thenReturn(4);
        when(task.getErrorMessage()).thenReturn("x");

        ExternalTaskRetryPlanner planner = new ExternalTaskRetryPlanner(null, null, null);
        RetryPlan plan = planner.plan("R5/PT1M", task);

        assertEquals(3, plan.nextRetries());
    }
}
