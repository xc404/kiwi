package com.kiwi.bpmn.component.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 从本地文件系统读取文本文件，按指定字符集解码为字符串并写入流程变量。
 */
@ComponentDescription(
        name = "文件读取",
        group = "文件",
        version = "1.0",
        description = "按路径读取文本文件内容（UTF-8 或指定编码）；可选单次读取最大字节数，防止过大文件占用内存",
        inputs = {
                @ComponentParameter(
                        key = "path",
                        name = "path",
                        description = "待读取文件的绝对或相对路径（不允许包含 .. 段）",
                        required = true),
                @ComponentParameter(
                        key = "encoding",
                        name = "encoding",
                        description = "字符集名称，例如 UTF-8、GBK；留空则使用 UTF-8",
                        schema = @Schema(defaultValue = "UTF-8")),
                @ComponentParameter(
                        key = "maxBytes",
                        name = "maxBytes",
                        description = "可选，最多读取的字节数（含）；不配置则不限制（大文件请谨慎）",
                        schema = @Schema(defaultValue = ""))
        },
        outputs = {
                @ComponentParameter(
                        key = "content",
                        name = "content",
                        description = "写入文件正文（字符串）的流程变量名",
                        schema = @Schema(defaultValue = "fileContent"))
        })
@Component("fileRead")
public class FileReadActivity extends AbstractBpmnActivityBehavior {

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        String pathStr =
                ExecutionUtils.getStringInputVariable(execution, "path")
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .orElseThrow(() -> new IllegalArgumentException("path 不能为空"));
        rejectUnsafePath(pathStr);

        Path path = Path.of(pathStr);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("文件不存在: " + pathStr);
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("path 不是普通文件: " + pathStr);
        }

        Charset charset = resolveCharset(execution);
        int maxBytes = ExecutionUtils.getIntInputVariable(execution, "maxBytes").orElse(0);
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes 不能为负数");
        }
        long size = Files.size(path);
        if (maxBytes > 0 && size > maxBytes) {
            throw new IllegalArgumentException(
                    "文件大小 " + size + " 字节超过 maxBytes " + maxBytes);
        }

        String text;
        try {
            text = Files.readString(path, charset);
        } catch (IOException e) {
            throw new IllegalStateException("读取文件失败: " + e.getMessage(), e);
        }

        String outVar = ExecutionUtils.getOutputVariableName(execution, "content");
        if (outVar != null) {
            execution.setVariable(outVar, text);
        }

        super.leave(execution);
    }

    static void rejectUnsafePath(String pathStr) {
        for (Path segment : Path.of(pathStr).normalize()) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("path 不允许包含 .. 路径段");
            }
        }
    }

    private static Charset resolveCharset(ActivityExecution execution) {
        return ExecutionUtils.getStringInputVariable(execution, "encoding")
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(
                        name -> {
                            try {
                                return Charset.forName(name);
                            } catch (Exception e) {
                                throw new IllegalArgumentException("不支持的 encoding: " + name, e);
                            }
                        })
                .orElse(StandardCharsets.UTF_8);
    }
}
