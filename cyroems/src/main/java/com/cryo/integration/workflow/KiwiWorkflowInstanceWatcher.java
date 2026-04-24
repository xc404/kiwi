package com.cryo.integration.workflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 基于轮询的流程实例状态监听，直到 {@link KiwiProcessInstanceState#isTerminalEnded()} 或轮询返回空（404）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KiwiWorkflowInstanceWatcher {

    private final KiwiWorkflowClient kiwiWorkflowClient;
    private final KiwiWorkflowProperties properties;
    private final TaskScheduler taskScheduler;

    /**
     * 周期性拉取实例状态；进入终态后取消调度并执行 {@code onTerminal}。
     *
     * @param pollInterval 为 null 时使用配置中的 {@code client.default-poll-interval-millis}
     * @return 可手动 {@link ScheduledFuture#cancel(boolean)} 的调度句柄
     */
    public ScheduledFuture<?> watchUntilTerminal(
            String instanceId,
            Duration pollInterval,
            Consumer<KiwiProcessInstanceState> onEachPoll,
            Runnable onTerminal) {
        Duration interval = pollInterval != null
                ? pollInterval
                : Duration.ofMillis(properties.getClient().getDefaultPollIntervalMillis());
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

        Runnable task = () -> {
            if (!kiwiWorkflowClient.isClientConfigured()) {
                log.warn("Kiwi client not configured, stop watching instance {}", instanceId);
                cancelRef(futureRef);
                return;
            }
            try {
                var opt = kiwiWorkflowClient.getProcessInstanceState(instanceId);
                if (opt.isEmpty()) {
                    return;
                }
                KiwiProcessInstanceState state = opt.get();
                if (onEachPoll != null) {
                    onEachPoll.accept(state);
                }
                if (state.isTerminalEnded()) {
                    cancelRef(futureRef);
                    if (onTerminal != null) {
                        onTerminal.run();
                    }
                }
            } catch (Exception e) {
                log.warn("watch poll failed for instance {}: {}", instanceId, e.getMessage());
            }
        };

        ScheduledFuture<?> future = taskScheduler.scheduleWithFixedDelay(task, Instant.now(), interval);
        futureRef.set(future);
        return future;
    }

    private static void cancelRef(AtomicReference<ScheduledFuture<?>> futureRef) {
        ScheduledFuture<?> f = futureRef.get();
        if (f != null && !f.isCancelled()) {
            f.cancel(false);
        }
    }
}
