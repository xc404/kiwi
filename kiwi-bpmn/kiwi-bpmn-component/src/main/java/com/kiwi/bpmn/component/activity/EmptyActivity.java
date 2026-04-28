package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

/**
 * 空 Activity：不执行业务逻辑，仅作为变量赋值占位节点。
 */
@ComponentDescription(
        name = "赋值组件",
        group = "通用",
        version = "1.0",
        description = "不执行业务逻辑，仅用于在节点上通过输入/输出参数映射完成变量赋值",
        inputs = {},
        outputs = {})
@Component("emptyActivity")
public class EmptyActivity extends AbstractBpmnActivityBehavior {

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        super.leave(execution);
    }
}
