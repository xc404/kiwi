package com.kiwi.bpmn.example;

import com.kiwi.bpmn.core.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@ComponentDescription(
        name = "Demo 问候",
        group = "示例",
        version = "1.0",
        description = "第三方组件示例：读取 name，写入 greeting",
        inputs = {
                @ComponentParameter(key = "name", description = "称呼", required = true)
        },
        outputs = {
                @ComponentParameter(key = "greeting", description = "问候语")
        })
@Component("demoGreeting")
public class DemoGreetingActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String name = ExecutionUtils.getStringInputVariable(execution, "name")
                .orElseThrow(() -> new IllegalArgumentException("name 不能为空"));
        execution.setVariable("greeting", "Hello, " + name);
    }
}
