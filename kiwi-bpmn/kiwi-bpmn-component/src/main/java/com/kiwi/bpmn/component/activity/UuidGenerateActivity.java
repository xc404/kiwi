package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.core.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@ComponentDescription(
        name = "生成 UUID",
        group = "通用",
        version = "1.0",
        description = "生成随机 UUID 字符串并写入流程变量",
        inputs = {},
        outputs = {
                @ComponentParameter(
                        key = "uuid",
                        description = "UUID 字符串",
                        schema = @Schema(defaultValue = "uuid"))
        })
@Component("uuidGenerate")
public class UuidGenerateActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String outVar = ExecutionUtils.getOutputVariableName(execution, "uuid");
        if (outVar == null || outVar.isBlank()) {
            outVar = "uuid";
        }
        execution.setVariable(outVar, UUID.randomUUID().toString());
    }
}
