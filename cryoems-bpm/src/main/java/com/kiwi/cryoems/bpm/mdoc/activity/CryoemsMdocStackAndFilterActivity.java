package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import com.kiwi.cryoems.bpm.mdoc.support.MdocStackInputBuilder;
import com.kiwi.cryoems.bpm.mdoc.support.MdocStackInputBuilder.MdocStackInputs;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * mdoc stack 阶段 —— 自动过滤分支：等价 cyroems
 * {@code com.cryo.task.tilt.stack.MdocStackHandler#handle} 中
 * {@code mDoc.isManualRebuild() == false} 的 {@code softwareService.stack_and_filter(...)} 路径。
 *
 * <p>本 Activity 同步调用 {@code stack_and_filter} 命令产出
 * {@code ${name}_raw_bin.mrc} / {@code ${name}_raw_bin.rawtilt} / {@code ${name}_raw_bin_files.txt}；
 * 当过滤后文件数小于输入时，反查 {@link com.kiwi.cryoems.bpm.movie.model.MovieResult#getMovie_data_id()}
 * 并回写 {@link MDoc#getMovie_data_ids()}。手动重建场景应改走
 */
@ComponentDescription(
        name = "CryoEMS Mdoc Stack 与过滤",
        group = "CryoEM",
        version = "1.0",
        description =
                "按 mdoc.tilts 顺序聚合 motion.dw.path，本地同步执行 stack_and_filter，"
                        + "读取 _raw_bin_files.txt 回写 MDoc.movie_data_ids；适用于 manualRebuild=false。",
        inputs = {
                @ComponentParameter(
                        key = "mdoc_id",
                        name = "MDocInstance id",
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
                        description = "stack_and_filter 实际保留的 motion.dw.path 列表",
                        schema = @Schema(defaultValue = "mdocStackFiles")),
                @ComponentParameter(
                        key = "mdocStackOutputFile",
                        name = "outputFile",
                        description = "${workDir}/${name}_raw_bin.mrc 绝对路径",
                        schema = @Schema(defaultValue = "mdocStackOutputFile")),
                @ComponentParameter(
                        key = "mdocStackTitleFile",
                        name = "titleFile",
                        description = "stack_and_filter 输出的 ${name}_raw_bin.rawtilt 绝对路径",
                        schema = @Schema(defaultValue = "mdocStackTitleFile")),
                @ComponentParameter(
                        key = "mdocStackExcludeFile",
                        name = "excludeFile",
                        description = "${workDir}/${name}_raw_bin_files.txt 绝对路径",
                        schema = @Schema(defaultValue = "mdocStackExcludeFile"))

        })
@Component("cyroemsMdocStackAndFilterActivity")
@RequiredArgsConstructor
@Slf4j
public class CryoemsMdocStackAndFilterActivity implements JavaDelegate {

    /**
     * 默认指向 {@code cryoems-bpm/script/mdoc_stack_and_filter.sh}（透传 conda 激活 + stack_exclude.py），
     * 字面顺序对齐 cyroems {@code SoftwareExe.stack_and_filter}；测试 / 迁移期可由配置覆盖
     * （例如 cyroems 旧别名 {@code stack_and_filter}）。
     */
    @Value("${cryoems.bpm.mdoc.stack-and-filter-command:mdoc_stack_and_filter.sh}")
    private String stackAndFilterCommand;

    @Value("${cryoems.bpm.mdoc.stack-and-filter-timeout-seconds:600}")
    private long timeoutSeconds;

    private final MdocStackInputBuilder inputBuilder;
    private final MDocRepository mDocRepository;

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
            throw new BpmnError("MDOC_STACK_INTERRUPTED", "stack_and_filter 被中断", e);
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
        if (!org.springframework.util.StringUtils.hasText(workDirRoot)) {
            throw new IllegalArgumentException("流程变量 work_dir 不能为空");
        }

        MdocStackInputs inputs = inputBuilder.build( instanceId, workDirRoot);
        if (inputs.mDoc().isManualRebuild()) {
            log.warn(
                    "mdoc.manualRebuild=true 命中本 Activity（应走 mdoc_stack 手动分支）"
                    );
        }

        runStackAndFilter(inputs);

        File excludeFile = new File(inputs.paths().excludeFile());
        List<String> keptFiles = readKeptFiles(excludeFile, inputs.files());

        if (keptFiles.size() != inputs.files().size()) {
            updateMovieDataIds(inputs, keptFiles);
        }

        execution.setVariable("mdocStackFiles", keptFiles);
        execution.setVariable("mdocStackOutputFile", inputs.paths().outputMrc());
        // 与 cyroems 原实现一致：本分支的 titleFile 改用 stack_and_filter 输出的 _raw_bin.rawtilt
        execution.setVariable("mdocStackTitleFile", inputs.paths().outputTitleFile());
        execution.setVariable("mdocStackExcludeFile", inputs.paths().excludeFile());

        log.info(
                "mdoc stack_and_filter 完成:  instanceId={}, kept={}/{}, output={}",
                instanceId,
                keptFiles.size(),
                inputs.files().size(),
                inputs.paths().outputMrc());
    }

    /**
     * 字面顺序对齐 cyroems {@code SoftwareService#stack_and_filter}：
     * {@code <cmd> --files a,b,c --input_tilt rawtlt --output mrc}。
     * 包级可见以便测试用 Mockito spy 覆盖（避免在 CI 上真正执行 IMOD）。
     */
    void runStackAndFilter(MdocStackInputs inputs) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>();
        argv.add(stackAndFilterCommand);
        argv.add("--files");
        argv.add(String.join(",", inputs.files()));
        argv.add("--input_tilt");
        argv.add(inputs.paths().rawTitleFile());
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
                    "stack_and_filter 超时（" + timeoutSeconds + "s）: " + argv);
        }
        int exit = process.exitValue();
        if (exit != 0) {
            throw new IllegalStateException(
                    "stack_and_filter 退出码 " + exit + "，命令: " + argv );
        }
    }


    /**
     * 读取 {@code _raw_bin_files.txt}（cyroems 写入逗号分隔的保留路径列表）。
     * 等价 {@code MdocStackHandler.handle} 第 91-98 行。
     */
    private static List<String> readKeptFiles(File excludeFile, List<String> fallback) throws IOException {
        if (!excludeFile.exists()) {
            // stack_and_filter 未产出 exclude 文件 = 没有任何过滤，沿用原输入。
            return new ArrayList<>(fallback);
        }
        String text = Files.readString(excludeFile.toPath(), StandardCharsets.UTF_8);
        if (StringUtils.isBlank(text)) {
            return new ArrayList<>(fallback);
        }
        return Arrays.stream(StringUtils.split(text, ","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 反查 {@link MovieResult#getMovie_data_id()} 并回写 {@link MDoc#getMovie_data_ids()}。
     * 等价 {@code MdocStackHandler.handle} 第 100-107 行。
     */
    private void updateMovieDataIds(MdocStackInputs inputs, List<String> keptFiles) {
        List<String> originalMotionIds = inputs.sortedTilts().stream()
                .map(MdocTiltMeta::getMotionResultId)
                .toList();
        Iterable<MovieResult> originalMovies = inputBuilder.findMovieResultsByMotionIds(originalMotionIds);
        List<String> newDataIds = inputBuilder.mapPathsToMovieDataIds(originalMovies, keptFiles);

        MDoc mDoc = inputs.mDoc();
        mDoc.setMovie_data_ids(newDataIds);
        mDocRepository.save(mDoc);
        log.info(
                "stack_and_filter 过滤后回写 MDoc.movie_data_ids: mdocId={}, before={}, after={}",
                mDoc.getId(),
                inputs.files().size(),
                newDataIds.size());
    }
}
