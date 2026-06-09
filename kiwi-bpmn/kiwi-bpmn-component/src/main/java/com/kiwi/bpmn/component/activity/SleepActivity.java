package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.core.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@ComponentDescription(
        name = "延时等待",
        group = "通用",
        version = "1.0",
        description = "阻塞当前执行线程指定毫秒数（短延时；长任务请用 External Task 或 Timer）",
        inputs = {
                @ComponentParameter(
                        key = "millis",
                        description = "等待毫秒数",
                        required = true,
                        schema = @Schema(defaultValue = "1000"))
        })
@Component("sleep")
public class SleepActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) throws InterruptedException {
        long millis =
                ExecutionUtils.getNumberInputVariable(execution, "millis")
                        .map(Double::longValue)
                        .orElse(1000L);
        if (millis < 0) {
            throw new IllegalArgumentException("millis 不能为负数");
        }
        if (millis > TimeUnit.MINUTES.toMillis(5)) {
            throw new IllegalArgumentException("millis 不得超过 5 分钟，请使用 Timer 或 External Task");
        }
        Thread.sleep(millis);
    }
}
