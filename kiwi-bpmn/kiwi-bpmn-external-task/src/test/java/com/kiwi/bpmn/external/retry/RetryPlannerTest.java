package com.kiwi.bpmn.external.retry;

import com.kiwi.bpmn.core.retry.IRetry;
import com.kiwi.bpmn.core.retry.JobRetryExceptionClassifier;
import com.kiwi.bpmn.core.retry.RetryPlan;
import org.camunda.bpm.client.task.ExternalTask;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void nonDecreasingIRetry_keepsExistingRetriesAndUsesConfiguredCycle() {
        ExternalTask task = Mockito.mock(ExternalTask.class);
        when(task.getRetries()).thenReturn(2);
        when(task.getErrorMessage()).thenReturn("prev");

        ExternalTaskRetryPlanner planner =
                new ExternalTaskRetryPlanner(
                        Mockito.mock(JobRetryExceptionClassifier.class),
                        Mockito.mock(ExternalTaskRetryCycleResolver.class),
                        "R5/PT1M",
                        "R5/PT30S");

        RetryPlan plan = planner.plan(task, new FakeOverloadException("overloaded"));

        assertEquals(2, plan.nextRetries());
        assertTrue(plan.retryTimeoutMs() > 0, "retryTimeoutMs should be positive");
        assertTrue(
                plan.retryTimeoutMs() <= 30_000L,
                "retryTimeoutMs should be <= 30s, got " + plan.retryTimeoutMs());
    }

    @Test
    void nonDecreasingIRetry_firstFailureUsesCycleRetriesAsInitial() {
        ExternalTask task = Mockito.mock(ExternalTask.class);
        when(task.getRetries()).thenReturn(null);
        when(task.getErrorMessage()).thenReturn(null);

        ExternalTaskRetryPlanner planner =
                new ExternalTaskRetryPlanner(
                        Mockito.mock(JobRetryExceptionClassifier.class),
                        Mockito.mock(ExternalTaskRetryCycleResolver.class),
                        "R5/PT1M",
                        "R5/PT30S");

        RetryPlan plan = planner.plan(task, new FakeOverloadException("overloaded"));

        assertEquals(5, plan.nextRetries());
    }

    @Test
    void decreasingIRetry_goesThroughStandardCycle() {
        ExternalTask task = Mockito.mock(ExternalTask.class);
        when(task.getRetries()).thenReturn(4);
        when(task.getErrorMessage()).thenReturn("prev");

        JobRetryExceptionClassifier classifier = f -> true;
        ExternalTaskRetryCycleResolver resolver = Mockito.mock(ExternalTaskRetryCycleResolver.class);
        when(resolver.resolveFromBpmn(task)).thenReturn(java.util.Optional.empty());

        ExternalTaskRetryPlanner planner =
                new ExternalTaskRetryPlanner(classifier, resolver, "R5/PT1M", "R5/PT30S");

        RetryPlan plan = planner.plan(task, new FakeDecreasingRetryException("normal"));

        assertEquals(3, plan.nextRetries());
    }

    /** 测试用 stub：实现 {@link IRetry} 并覆盖 {@code decreaseRetries()} 为 {@code false}，模拟过载语义。 */
    static final class FakeOverloadException extends RuntimeException implements IRetry {
        FakeOverloadException(String msg) {
            super(msg);
        }

        @Override
        public boolean decreaseRetries() {
            return false;
        }
    }

    /** 测试用 stub：实现 {@link IRetry} 但保留默认 {@code decreaseRetries()=true}，应走标准分支。 */
    static final class FakeDecreasingRetryException extends RuntimeException implements IRetry {
        FakeDecreasingRetryException(String msg) {
            super(msg);
        }
    }
}
