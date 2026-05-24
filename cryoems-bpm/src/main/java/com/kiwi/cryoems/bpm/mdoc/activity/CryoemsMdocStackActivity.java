package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.mdoc.support.MdocStackInputBuilder;
import com.kiwi.cryoems.bpm.mdoc.support.MdocStackInputBuilder.MdocStackInputs;
import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * mdoc stack 阶段 —— 手动重建分支：等价 cyroems
 * {@code com.cryo.task.tilt.stack.MdocStackHandler#handle} 中
 * {@code mDoc.isManualRebuild() == true} 的 {@code softwareService.mdoc_stack(files, outputFile)} 路径。
 *
 * <p>本 Activity 在装配输入（{@code ${name}.rawtlt} / 文件列表）后，<strong>同步执行
 * {@code mdoc_stack.sh --files a,b,c --output xxx}</strong>，等待退出码并把日志落到
 * {@code ${workDir}/${name}_mdoc_stack.log}。同时仍写出 {@code ${name}_mdoc_stack.sh}
 * 作为可复用的脚本产物（便于 SLURM / 人工重跑），并把脚本路径回写到流程变量。</p>
 *
 * <p>非手动重建场景应改走
 * {@link CryoemsMdocStackAndFilterActivity}（{@code stack_and_filter} 同步执行 + 过滤回写）。</p>
 */
@ComponentDescription(
        name = "CryoEMS Mdoc Stack",
        group = "CryoEM",
        version = "1.0",
        description =
                "按 mdoc.tilts 顺序聚合 motion.dw.path，写 ${name}.rawtlt 与 mdoc_stack.sh，"
                        + "本地同步执行 mdoc_stack 产出 ${name}_raw_bin.mrc；仅适用于 mDoc.manualRebuild=true。",
        inputs = {
                @ComponentParameter(
                        key = "mdoc_id",
                        name = "MDocid",
                        description = "MDocInstance 主键，等价 cyroems context.getInstance().getId()",
                        required = true),
                @ComponentParameter(
                        key = "work_dir",
                        name = "工作目录根",
                        description = "cryoEMS 端 ${app.file.work_dir}/{taskRoot}（与 movie 预处理同形）",
                        required = true)
        },
        outputs = {
                @ComponentParameter(
                        key = "mdocStackFiles",
                        name = "files",
                        description = "聚合后的 motion.dw.path 列表（List<String>，与 tilts 排序一致）",
                        schema = @Schema(defaultValue = "mdocStackFiles")),
                @ComponentParameter(
                        key = "mdocStackOutputFile",
                        name = "outputFile",
                        description = "${workDir}/${name}_raw_bin.mrc 绝对路径（已由 mdoc_stack 同步产出）",
                        schema = @Schema(defaultValue = "mdocStackOutputFile")),
                @ComponentParameter(
                        key = "mdocStackTitleFile",
                        name = "titleFile",
                        description = "已写盘的 ${name}.rawtlt 绝对路径",
                        schema = @Schema(defaultValue = "mdocStackTitleFile")),
        })
@Component("cyroemsMdocStackActivity")
@RequiredArgsConstructor
@Slf4j
public class CryoemsMdocStackActivity implements JavaDelegate {

    /**
     * 默认指向 {@code cryoems-bpm/script/mdoc_stack.sh}（透传 conda 激活 + mrc_stack.py），
     * 与 cyroems {@code SoftwareExe.mdoc_stack} 的命令字面顺序保持一致；
     * 测试 / 迁移期可由配置覆盖（例如 cyroems 旧别名 {@code mdoc_stack}）。
     */
    @Value("${cryoems.bpm.mdoc.stack-command:mdoc_stack.sh}")
    private String stackCommand;

    /** mdoc_stack 同步执行超时（秒）。手动重建分支默认与 stack_and_filter 对齐为 600s。 */
    @Value("${cryoems.bpm.mdoc.stack-timeout-seconds:600}")
    private long timeoutSeconds;

    private final MdocStackInputBuilder inputBuilder;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch (BpmnError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BpmnError("MDOC_STACK_INPUT", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BpmnError("MDOC_STACK_INTERRUPTED", "mdoc_stack 被中断", e);
        } catch (IOException e) {
            throw new BpmnError("MDOC_STACK_IO", "mdoc stack IO 失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new BpmnError("MDOC_STACK", "mdoc stack 失败: " + e.getMessage(), e);
        }
    }

    private void doExecute(DelegateExecution execution) throws IOException, InterruptedException {
        String instanceId = ExecutionUtils.getStringInputVariable(execution, "mdoc_id")
                .orElseThrow(() -> new IllegalArgumentException("流程变量 mdoc_id 不能为空"));
        String workDirRoot = WorkflowVariableReader.resolveWorkDir(execution);
        if (!StringUtils.hasText(workDirRoot)) {
            throw new IllegalArgumentException("流程变量 work_dir 不能为空");
        }

        MdocStackInputs inputs = inputBuilder.build(instanceId, workDirRoot);
        if (!inputs.mDoc().isManualRebuild()) {
            // 不强行失败：流程编排可能把 manualRebuild 判断放在网关上，但若误调用本节点给出明确提示。
            log.warn(
                    "mdoc.manualRebuild=false 命中本 Activity（应走 stack_and_filter）: mdocId={}", instanceId
                    );
        }


        runStack(inputs);

        execution.setVariable("mdocStackFiles", inputs.files());
        execution.setVariable("mdocStackOutputFile", inputs.paths().outputMrc());
        execution.setVariable("mdocStackTitleFile", inputs.paths().rawTitleFile());

        log.info(
                "mdoc stack（manualRebuild）完成:  instanceId={}, tilts={}, output={}",
                instanceId,
                inputs.files().size(),
                inputs.paths().outputMrc());
    }


    /**
     * 同步执行 {@code mdoc_stack.sh --files a,b,c --output xxx}，与
     * {@link CryoemsMdocStackAndFilterActivity#runStackAndFilter} 同款 ProcessBuilder 模式。
     * 包级可见以便测试用 Mockito spy 覆盖（避免在 CI 上真正触发 conda / mrc_stack.py）。
     */
    void runStack(MdocStackInputs inputs) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>();
        argv.add(stackCommand);
        argv.add("--files");
        argv.add(String.join(",", inputs.files()));
        argv.add("--output");
        argv.add(inputs.paths().outputMrc());

        ProcessBuilder pb = new ProcessBuilder(argv)
                .directory(inputs.workDir())
                .redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new BpmnError(
                    "MDOC_STACK_TIMEOUT",
                    "mdoc_stack 超时（" + timeoutSeconds + "s）: " + argv);
        }
        int exit = process.exitValue();
        if (exit != 0) {
            throw new IllegalStateException(
                    "mdoc_stack 退出码 " + exit + "，命令: " + argv );
        }
    }

}
