package com.kiwi.cryoems.bpm.activity;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.model.ClosetScale;
import com.kiwi.cryoems.bpm.model.MrcMetadata;
import com.kiwi.cryoems.bpm.support.MrcHeaderParser;
import com.kiwi.cryoems.bpm.support.MicroscopeScaleRegistry;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * cryoems 预处理：对电影文件执行 IMOD {@code header}，解析为 {@link MrcMetadata}；根据流程变量 {@code microscope}
 * 与 {@code p_size} 选取最近 {@link ClosetScale}。
 */
@ComponentDescription(
        name = "CryoEMS 预处理",
        group = "CryoEM",
        version = "1.0",
        description = "对电影文件执行 IMOD header，解析 MRC 元数据；根据 microscope 与 p_size 从标尺注册表选取最近的 ClosetScale。"
                + "电影路径来自 movieFile，或流程变量 movie 中的 file_path / filePath。",
        inputs = {
                @ComponentParameter(
                        key = "movieFile",
                        name = "movieFile",
                        description = "电影文件路径（字符串）；可与 movie.file_path 二选一",
                        required = true
                ),
                @ComponentParameter(
                        key = "microscope",
                        name = "microscope",
                        description = "显微镜标识，用于标尺匹配",
                        required = true),
                @ComponentParameter(
                        key = "p_size",
                        name = "p_size",
                        description = "像素尺寸，用于选取最近标尺",
                        required = true)
        },
        outputs = {
                @ComponentParameter(
                        key = "mrcMetadata",
                        name = "mrcMetadata",
                        description = "MRC 头解析结果（MrcMetadata）写入的流程变量名",
                        schema = @Schema(defaultValue = "mrcMetadata")),
                @ComponentParameter(
                        key = "closetScale",
                        name = "closetScale",
                        description = "匹配的标尺档位（ClosetScale）写入的流程变量名",
                        schema = @Schema(defaultValue = "closetScale"))
        })
@Component("cyroemsPrepareActivity")
@RequiredArgsConstructor
public class CyroemsPrepareActivity implements JavaDelegate {

    private final MicroscopeScaleRegistry microscopeScaleRegistry;

    @Value("${cryoems.bpm.header-command:header}")
    private String headerCommand;

    @Value("${cryoems.bpm.header-timeout-seconds:120}")
    private long headerTimeoutSeconds;

    @Override
    public void execute(DelegateExecution execution) {
        String movieFile = resolveMovieFile(execution);
        String microscope = readString(execution, "microscope");
        double pSize = readPSize(execution);

        Path headerOut;
        try {
            headerOut = Files.createTempFile("cryoems-header-", ".txt");
        } catch (IOException e) {
            throw new BpmnError("PREPARE_IO", "无法创建 header 临时输出: " + e.getMessage(), e);
        }

        try {
            runHeader(movieFile, headerOut);
            String text = Files.readString(headerOut, StandardCharsets.UTF_8);
            MrcMetadata meta = MrcHeaderParser.parse(text, movieFile);
            execution.setVariable("mrcMetadata", meta);

            ClosetScale scale = microscopeScaleRegistry.closestScale(microscope, pSize);
            execution.setVariable("closetScale", scale);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BpmnError("PREPARE_INTERRUPTED", "header 执行被中断", e);
        } catch (Exception e) {
//            throw new BpmnError("PREPARE_FAILED", "cryoems 预处理失败: " + e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                Files.deleteIfExists(headerOut);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private void runHeader(String movieFile, Path headerOut)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(headerCommand, movieFile);
        pb.redirectOutput(headerOut.toFile());
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        Process p = pb.start();
        boolean finished = p.waitFor(headerTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IllegalStateException(
                    "header 超时（" + headerTimeoutSeconds + "s）: " + headerCommand + " " + movieFile);
        }
        int code = p.exitValue();
        if (code != 0) {
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(
                    "header 退出码 "
                            + code
                            + " 命令: "
                            + headerCommand
                            + " "
                            + movieFile
                            + (err.isBlank() ? "" : "\nstderr: " + err));
        }
    }

    private static String resolveMovieFile(DelegateExecution execution) {
        Object direct = execution.getVariable("movieFile");
        if (direct instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        Object movie = execution.getVariable("movie");
        if (movie instanceof Map<?, ?> m) {
            Object path = m.get("file_path");
            if (path == null) {
                path = m.get("filePath");
            }
            if (path != null) {
                String p = path.toString().trim();
                if (!p.isEmpty()) {
                    return p;
                }
            }
        }
        throw new IllegalArgumentException("需要流程变量 movieFile（字符串）或 movie.file_path");
    }

    private static String readString(DelegateExecution execution, String name) {
        Object v = execution.getVariable(name);
        if (v == null) {
            throw new IllegalArgumentException("流程变量 " + name + " 不能为空");
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("流程变量 " + name + " 不能为空");
        }
        return s;
    }

    private static double readPSize(DelegateExecution execution) {
        Object v = execution.getVariable("p_size");
        if (v == null) {
            throw new IllegalArgumentException("流程变量 p_size 不能为空");
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        String s = v.toString().trim();
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("p_size 不是合法数字: " + v, e);
        }
    }
}
