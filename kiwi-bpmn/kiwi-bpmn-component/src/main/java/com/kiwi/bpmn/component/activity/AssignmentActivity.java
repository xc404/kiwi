package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import org.operaton.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

/**
 * 变量组件：通过 BPMN 服务任务的输入/输出映射配置流程变量，运行时仅负责流转。
 */
@ComponentDescription(
        name = "变量组件",
        group = "通用",
        version = "1.0",
        description = "变量组件，通过自定义变量实现流程变量赋值",
        inputs = {},
        outputs = {})
@Component("assignmentActivity")
public class AssignmentActivity extends AbstractBpmnActivityBehavior {

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        super.leave(execution);
    }
}
