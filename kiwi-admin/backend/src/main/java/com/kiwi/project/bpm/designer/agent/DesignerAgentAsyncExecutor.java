package com.kiwi.project.bpm.designer.agent;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 独立 Bean，供 {@link DesignerAgentSessionService} 异步调度 Graph；
 * 避免同类自调用导致 {@code @Async} 失效。
 */
@Component
public class DesignerAgentAsyncExecutor {

    @Async
    public void execute(Runnable task) {
        task.run();
    }
}
