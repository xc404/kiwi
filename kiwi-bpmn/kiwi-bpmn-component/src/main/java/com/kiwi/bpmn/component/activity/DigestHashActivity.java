package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@ComponentDescription(
        name = "摘要哈希",
        group = "通用",
        version = "1.0",
        description = "对输入字符串计算 MD5 或 SHA-256 十六进制摘要",
        inputs = {
                @ComponentParameter(key = "input", description = "待哈希文本", required = true),
                @ComponentParameter(
                        key = "algorithm",
                        description = "md5 或 sha256",
                        schema = @Schema(defaultValue = "sha256"))
        },
        outputs = {
                @ComponentParameter(
                        key = "digest",
                        description = "十六进制摘要",
                        schema = @Schema(defaultValue = "digest"))
        })
@Component("digestHash")
public class DigestHashActivity implements JavaDelegate {

    private static final Set<String> ALLOWED = Set.of("MD5", "SHA-256");

    @Override
    public void execute(DelegateExecution execution) throws NoSuchAlgorithmException {
        String input =
                ExecutionUtils.getStringInputVariable(execution, "input")
                        .orElseThrow(() -> new IllegalArgumentException("流程变量 input 不能为空"));
        String algoRaw =
                ExecutionUtils.getStringInputVariable(execution, "algorithm").orElse("sha256");
        String jcaName = normalizeAlgorithm(algoRaw);

        MessageDigest digest = MessageDigest.getInstance(jcaName);
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        String hex = HexFormat.of().formatHex(hash);

        String outVar = ExecutionUtils.getOutputVariableName(execution, "digest");
        if (outVar != null && !outVar.isBlank()) {
            execution.setVariable(outVar, hex);
        }
    }

    private String normalizeAlgorithm(String raw) {
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        String jca = switch (upper) {
            case "MD5" -> "MD5";
            case "SHA256", "SHA-256" -> "SHA-256";
            default -> throw new IllegalArgumentException("algorithm 须为 md5 或 sha256，实际: " + raw);
        };
        if (!ALLOWED.contains(jca)) {
            throw new IllegalArgumentException("不支持的算法: " + raw);
        }
        return jca;
    }
}
