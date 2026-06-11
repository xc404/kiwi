package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.core.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

@ComponentDescription(
        name = "Base64 编解码",
        group = "通用",
        version = "1.0",
        description = "对文本进行 Base64 编码或解码（UTF-8）",
        inputs = {
                @ComponentParameter(key = "input", description = "输入文本", required = true),
                @ComponentParameter(
                        key = "mode",
                        description = "encode 或 decode",
                        required = true,
                        schema = @Schema(defaultValue = "encode"))
        },
        outputs = {
                @ComponentParameter(
                        key = "output",
                        description = "输出文本",
                        schema = @Schema(defaultValue = "output"))
        })
@Component("base64Codec")
public class Base64CodecActivity implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String input =
                ExecutionUtils.getStringInputVariable(execution, "input")
                        .orElseThrow(() -> new IllegalArgumentException("流程变量 input 不能为空"));
        String mode =
                ExecutionUtils.getStringInputVariable(execution, "mode")
                        .orElse("encode")
                        .toLowerCase(Locale.ROOT);

        String result =
                switch (mode) {
                    case "encode" ->
                            Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
                    case "decode" ->
                            new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
                    default -> throw new IllegalArgumentException("mode 须为 encode 或 decode，实际: " + mode);
                };

        String outVar = ExecutionUtils.getOutputVariableName(execution, "output");
        if (outVar != null && !outVar.isBlank()) {
            execution.setVariable(outVar, result);
        }
    }
}
