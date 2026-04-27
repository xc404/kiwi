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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 将流程中的字符串按指定字符集写入本地文件；可选择覆盖或追加，以及是否自动创建父目录。
 */
@ComponentDescription(
        name = "文件写入",
        group = "文件",
        version = "1.0",
        description = "将正文写入指定路径；默认覆盖已存在文件，可选追加模式；可选择在写入前创建缺失的父目录",
        inputs = {
                @ComponentParameter(
                        key = "path",
                        htmlType = "#text",
                        name = "path",
                        description = "目标文件绝对或相对路径（不允许包含 .. 段）",
                        required = true),
                @ComponentParameter(
                        key = "content",
                        htmlType = "#text",
                        name = "content",
                        description = "要写入文件的字符串内容",
                        required = true),
                @ComponentParameter(
                        key = "encoding",
                        htmlType = "#text",
                        name = "encoding",
                        description = "字符集名称，例如 UTF-8、GBK；留空则使用 UTF-8",
                        schema = @Schema(defaultValue = "UTF-8")),
                @ComponentParameter(
                        key = "append",
                        htmlType = "CheckBox",
                        name = "append",
                        description = "为 true 时在文件末尾追加；为 false 时覆盖已有文件",
                        schema = @Schema(defaultValue = "false")),
                @ComponentParameter(
                        key = "createDirectories",
                        htmlType = "CheckBox",
                        name = "createDirectories",
                        description = "为 true 时若父目录不存在则自动创建",
                        schema = @Schema(defaultValue = "false"))
        },
        outputs = {
                @ComponentParameter(
                        key = "bytesWritten",
                        htmlType = "#text",
                        name = "bytesWritten",
                        description = "可选，写入 UTF-8 字节长度（按实际编码统计）所写入的流程变量名",
                        schema = @Schema(defaultValue = ""))
        })
@Component("fileWrite")
public class FileWriteActivity extends AbstractBpmnActivityBehavior {

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        String pathStr =
                ExecutionUtils.getStringInputVariable(execution, "path")
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .orElseThrow(() -> new IllegalArgumentException("path 不能为空"));
        FileReadActivity.rejectUnsafePath(pathStr);

        String content =
                ExecutionUtils.getStringInputVariable(execution, "content")
                        .orElseThrow(() -> new IllegalArgumentException("content 不能为空"));

        Charset charset = resolveCharset(execution);
        boolean append = ExecutionUtils.getBooleanInputVariable(execution, "append").orElse(false);
        boolean createDirectories =
                ExecutionUtils.getBooleanInputVariable(execution, "createDirectories").orElse(false);

        Path path = Path.of(pathStr);
        if (createDirectories) {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        }

        List<StandardOpenOption> opts = new ArrayList<>(3);
        opts.add(StandardOpenOption.WRITE);
        opts.add(StandardOpenOption.CREATE);
        opts.add(append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);

        byte[] bytes = content.getBytes(charset);
        try {
            Files.write(path, bytes, opts.toArray(StandardOpenOption[]::new));
        } catch (IOException e) {
            throw new IllegalStateException("写入文件失败: " + e.getMessage(), e);
        }

        String bytesVar = ExecutionUtils.getOutputVariableName(execution, "bytesWritten");
        if (bytesVar != null && !bytesVar.isBlank()) {
            execution.setVariable(bytesVar, bytes.length);
        }

        super.leave(execution);
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
