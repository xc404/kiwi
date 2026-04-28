package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

import static com.kiwi.bpmn.component.utils.ExecutionUtils.getOutputVariableName;


@ComponentDescription(
        name = "测试组件",
        group = "测试组件",
        version = "1.0",
        description = "这是一个测试组件",
        inputs = {
                @ComponentParameter(key = "input1", name = "输入参数1"),
                @ComponentParameter(key = "input2", name = "输入参数2")
        },
        outputs = {
                @ComponentParameter(
                        key = "output1",
                        name = "输出1",
                        schema = @Schema(defaultValue = "output1")),
                @ComponentParameter(
                        key = "output2",
                        name = "输出2",
                        schema = @Schema(defaultValue = "output2"))
        }
)
@Component
public class TestActivity extends AbstractBpmnActivityBehavior implements org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior
{
    @Override
    public void execute(ActivityExecution execution)
    {
        System.out.println("执行了测试组件");
        Object input1 = execution.getVariable("input1");
        Object input2 = execution.getVariable("input2");
        System.out.println("输入参数1：" + input1);
        System.out.println("输入参数2：" + input2);
        String output1 = getOutputVariableName(execution, "output1");
        String output2 = getOutputVariableName(execution,"output2" );

        execution.setVariable(output1,"output1 结果");
        execution.setVariable(output2,"output2 结果");
        // 继续执行流程
        super.leave(execution);
    }

}
