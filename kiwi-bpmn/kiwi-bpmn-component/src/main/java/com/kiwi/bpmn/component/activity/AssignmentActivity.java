package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

/**
 */
@ComponentDescription(
        name = "赋值组件",
        group = "通用",
        version = "1.0",
        description = "赋值组件，通过自定义输出参数实现流程变量赋值",
        inputs = {},
        outputs = {})
@Component("assignmentActivity")
public class AssignmentActivity extends AbstractBpmnActivityBehavior {

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        super.leave(execution);
    }
}
