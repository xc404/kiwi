package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * mdoc 流水线预处理：根据流程变量 {@code work_dir} 在其下创建 {@code mdoc} 子目录。
 *
 * <p>等价 cryoems 端 {@code com.cryo.service.FilePathService#getMdocWorkDir} 路径前缀：
 * {@code ${work_dir}/mdoc}（不含 {@code instance.name}，instance 级目录由
 * {@link com.kiwi.cryoems.bpm.mdoc.support.MdocStackInputBuilder} 在 stack 阶段按需创建）。</p>
 *
 * <p>本 Activity 仅做目录就位，不读写 MongoDB，不依赖 {@code mdoc.id} / {@code mdoc.dataId}；
 * 作为 mdoc 流程的第一个节点，保证后续 stack / align 等节点写盘时父目录已存在。</p>
 */
@ComponentDescription(
        name = "CryoEMS Mdoc 预处理",
        group = "CryoEM",
        version = "1.0",
        description = "在 ${work_dir} 下创建 mdoc 子目录，作为 mdoc 流水线第一个节点的目录就位操作；"
                + "不读写数据库，不依赖 mdoc.id。",
        inputs = {
                @ComponentParameter(
                        key = "work_dir",
                        name = "工作目录根",
                        description = "cryoEMS 端 ${app.file.work_dir}/{taskRoot}（与 movie 预处理同形）",
                        required = true)
        },
        outputs = {
                @ComponentParameter(
                        key = "mdocWorkDir",
                        name = "mdocWorkDir",
                        description = "已创建的 ${work_dir}/mdoc 绝对路径",
                        schema = @Schema(defaultValue = "mdocWorkDir"))
        })
@Component("cyroemsMdocPrepareActivity")
@Slf4j
public class CryoemsMdocPrepareActivity implements JavaDelegate {

    static final String MDOC_SUBDIR = "mdoc";

    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch (BpmnError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BpmnError("MDOC_PREPARE_INPUT", e.getMessage(), e);
        } catch (IOException e) {
            throw new BpmnError("MDOC_PREPARE_IO", "创建 mdoc 子目录失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new BpmnError("MDOC_PREPARE", "mdoc 预处理失败: " + e.getMessage(), e);
        }
    }

    private void doExecute(DelegateExecution execution) throws IOException {
        String workDirRoot = WorkflowVariableReader.resolveWorkDir(execution);
        if (!StringUtils.hasText(workDirRoot)) {
            throw new IllegalArgumentException("流程变量 work_dir 不能为空");
        }

        Path root = Path.of(workDirRoot.trim());
        Files.createDirectories(root);
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("work_dir 不是目录: " + workDirRoot);
        }

        Path mdocDir = root.resolve(MDOC_SUBDIR);
        Files.createDirectories(mdocDir);
        if (!Files.isDirectory(mdocDir)) {
            throw new IllegalStateException("mdoc 子目录创建失败: " + mdocDir);
        }

        String absolute = mdocDir.toAbsolutePath().toString();
        execution.setVariable("mdocWorkDir", absolute);

        log.info("mdoc 子目录已就绪: work_dir={}, mdocWorkDir={}", workDirRoot, absolute);
    }
}
