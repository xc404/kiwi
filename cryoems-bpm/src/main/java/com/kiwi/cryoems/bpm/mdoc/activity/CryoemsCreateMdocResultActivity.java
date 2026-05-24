package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocInstanceRepository;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.dao.MdocResultRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MDocInstance;
import com.kiwi.cryoems.bpm.mdoc.model.MdocResult;
import com.kiwi.cryoems.bpm.mdoc.result.AlignReconResult;
import com.kiwi.cryoems.bpm.mdoc.result.CoarseAlignrResult;
import com.kiwi.cryoems.bpm.mdoc.result.PatchTrackingResult;
import com.kiwi.cryoems.bpm.mdoc.result.SeriesAlignResult;
import com.kiwi.cryoems.bpm.mdoc.result.StackResult;
import com.kiwi.cryoems.bpm.mdoc.support.MdocPathResolver;
import com.kiwi.cryoems.bpm.mdoc.support.MdocReconstructPaths;
import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;

/**
 * 创建并持久化 {@link MdocResult}：等价 cyroems 端 mdoc 流水线 stack + 重建阶段对
 * {@code com.cryo.model.MDocResult} 的最终装配落库。
 *
 * <p>本 Activity 应排在 {@code mdoc_reconstruct.sh}（{@link CryoemsMdocStackActivity} /
 * {@link CryoemsMdocStackAndFilterActivity} + 重建脚本）执行完成之后，按 {@link MdocReconstructPaths}
 * 一次性把以下子结果回写到 MdocResult 文档：</p>
 *
 * <ul>
 *     <li>基础键：{@code task_data_id} / {@code task_id} / {@code instance_id} / {@code data_id} /
 *         {@code config_id} / {@code category}；</li>
 *     <li>{@code meta} 由 {@link MDoc#getMeta()} 镜像；</li>
 *     <li>{@code stackResult} 由上游 stack Activity 输出的 {@code mdocStackFiles /
 *         mdocStackOutputFile / mdocStackTitleFile / mdocStackExcludeFile} 直接拼装；</li>
 *     <li>{@code coarseAlignrResult} / {@code patchTrackingResult} / {@code seriesAlignResult} /
 *         {@code alignReconResult} 由 {@link MdocPathResolver#resolveAll} 派生
 *         {@code mdoc_reconstruct.sh} 的全部约定产物路径；</li>
 *     <li>{@code images.mdoc_recon} 指向 {@code align_reconOutput} 缩略图（与 cyroems 行为一致）。</li>
 * </ul>
 *
 * <p>键映射（按 {@code .cursor/rules/component-parameter-key-no-dot.mdc} 全部为顶层扁平命名）：
 * <ul>
 *     <li>{@code data_id} ← 流程变量 {@code mdoc_dataId}（{@link MDoc#getId()}）；</li>
 *     <li>{@code instance_id} ← 流程变量 {@code mdoc_id}（{@link MDocInstance#getId()}）；</li>
 *     <li>{@code task_id} ← 流程变量 {@code task_id}，缺省回退 {@link MDocInstance#getTask_id()}；</li>
 *     <li>{@code task_data_id} ← 流程变量 {@code task_dataId} / {@code task_data_id}；</li>
 *     <li>{@code config_id} ← 流程变量 {@code task_config_id}；</li>
 *     <li>{@code category} ← 流程变量 {@code category}，默认 {@code default}。</li>
 * </ul>
 * 路径派生：
 * <ul>
 *     <li>{@code workDir} ← {@code mdocStackWorkDir}，缺省时由 {@code work_dir + /mdoc/ + name} 派生；</li>
 *     <li>{@code thumbDir} ← {@code mdocReconstructThumbDir}，缺省时退回 {@code work_dir + /thumbnails}。</li>
 * </ul></p>
 */
@ComponentDescription(
        name = "CryoEMS 创建 MdocResult",
        group = "CryoEM",
        version = "1.1",
        description =
                "按 mdoc_id / mdoc_dataId 装配 MdocResult 全字段：基础键 + meta + stack/coarse/patch/series/recon "
                        + "子结果 + mdoc_recon 缩略图，并以 rate=0 持久化。应在 mdoc_reconstruct.sh 完成之后调用。",
        inputs = {
                @ComponentParameter(
                        key = "mdoc_id",
                        name = "MDocInstance id",
                        description = "MDocInstance 主键，等价 cyroems context.getInstance().getId()",
                        required = true)
        },
        outputs = {
                @ComponentParameter(
                        key = "mdocResultId",
                        name = "mdocResultId",
                        description = "持久化后的 MdocResult 文档 id",
                        schema = @Schema(defaultValue = "mdocResultId")),
                @ComponentParameter(
                        key = "mdocResult",
                        name = "mdocResult",
                        description = "MdocResult 对象",
                        schema = @Schema(defaultValue = "mdocResult"))
        })
@Component("cyroemsCreateMdocResultActivity")
@RequiredArgsConstructor
@Slf4j
public class CryoemsCreateMdocResultActivity implements JavaDelegate {

    static final String DEFAULT_CATEGORY = "default";
    static final String DEFAULT_THUMB_DIR_NAME = "thumbnails";

    private final MDocRepository mDocRepository;
    private final MDocInstanceRepository mDocInstanceRepository;
    private final MdocResultRepository mdocResultRepository;
    private final MdocPathResolver mdocPathResolver;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch (BpmnError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BpmnError("MDOC_RESULT_INPUT", e.getMessage(), e);
        } catch (Exception e) {
            throw new BpmnError("MDOC_RESULT", "创建 MdocResult 失败: " + e.getMessage(), e);
        }
    }

    private void doExecute(DelegateExecution execution) {
        String instanceId = ExecutionUtils.getStringInputVariable(execution, "mdoc_id")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalArgumentException("流程变量 mdoc_id 不能为空"));
        String mdocDataId = ExecutionUtils.getStringInputVariable(execution, "mdoc_dataId")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalArgumentException("流程变量 mdoc_dataId 不能为空"));

        MDoc mDoc = mDocRepository
                .findById(mdocDataId)
                .orElseThrow(() -> new IllegalArgumentException("MDoc 不存在: " + mdocDataId));
        MDocInstance instance = mDocInstanceRepository
                .findById(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("MDocInstance 不存在: " + instanceId));
        if (!StringUtils.hasText(instance.getName())) {
            throw new IllegalArgumentException("MDocInstance.name 不能为空: " + instanceId);
        }

        MdocResult mdocResult = new MdocResult();
        applyKeys(mdocResult, execution, mDoc, instance);
        mdocResult.setMeta(mDoc.getMeta());
        mdocResult.setRate(0);

        MdocReconstructPaths paths = resolvePaths(execution, instance.getName());
        applyStackResult(mdocResult, execution, paths);
        applyReconstructResults(mdocResult, paths);
        applyMdocReconImage(mdocResult, paths);

        MdocResult saved = mdocResultRepository.save(mdocResult);

        execution.setVariable("mdocResultId", saved.getId());
        execution.setVariable("mdocResult", saved);

        log.info(
                "MdocResult 创建完成: id={}, dataId={}, instanceId={}, taskId={}, configId={}, "
                        + "category={}, workDir={}, thumbDir={}",
                saved.getId(),
                saved.getData_id(),
                saved.getInstance_id(),
                saved.getTask_id(),
                saved.getConfig_id(),
                saved.getCategory(),
                paths.workDir(),
                paths.thumbDir());
    }

    private static void applyKeys(
            MdocResult mdocResult,
            DelegateExecution execution,
            MDoc mDoc,
            MDocInstance instance) {
        String configId = ExecutionUtils.getStringInputVariable(execution, "task.config_id")
                .filter(StringUtils::hasText)
                .orElse(null);
        mdocResult.setInstance_id(instance.getId());
        mdocResult.setData_id(mDoc.getId());
        mdocResult.setTask_id(instance.getTask_id());
        mdocResult.setConfig_id(configId);
        mdocResult.setCategory(DEFAULT_CATEGORY);
    }

    /**
     * 解析 mdoc 全流程产物路径：
     * <ol>
     *     <li>{@code mdocStackWorkDir} 优先（由 stack Activity 写入），按 {@code .../mdoc/${name}} 反推 root；</li>
     *     <li>否则用 {@code work_dir} 派生 {@code ${work_dir}/mdoc/${name}}；</li>
     *     <li>{@code mdocReconstructThumbDir} 优先；否则 {@code ${work_dir}/thumbnails}。</li>
     * </ol>
     */
    private MdocReconstructPaths resolvePaths(DelegateExecution execution, String name) {
        String workDir = WorkflowVariableReader.readText(execution, "mdocStackWorkDir");
        String workDirRoot;
        if (StringUtils.hasText(workDir)) {
            // ${work_dir}/mdoc/${name}  ->  ${work_dir}
            Path mdocWorkDir = Path.of(workDir).toAbsolutePath().normalize();
            Path parent = mdocWorkDir.getParent();
            if (parent == null || parent.getFileName() == null
                    || !"mdoc".equals(parent.getFileName().toString())) {
                throw new IllegalArgumentException(
                        "mdocStackWorkDir 不符合 ${work_dir}/mdoc/${name} 字面: " + workDir);
            }
            Path root = parent.getParent();
            if (root == null) {
                throw new IllegalArgumentException("mdocStackWorkDir 缺少上级目录: " + workDir);
            }
            workDirRoot = root.toString();
        } else {
            workDirRoot = WorkflowVariableReader.resolveWorkDir(execution);
            if (!StringUtils.hasText(workDirRoot)) {
                throw new IllegalArgumentException("流程变量 work_dir / mdocStackWorkDir 至少需要其一");
            }
        }

        String thumbDir = WorkflowVariableReader.readText(execution, "mdocReconstructThumbDir");
        if (!StringUtils.hasText(thumbDir)) {
            thumbDir = Path.of(workDirRoot).resolve(DEFAULT_THUMB_DIR_NAME)
                    .toAbsolutePath().normalize().toString();
        }
        return mdocPathResolver.resolveAll(workDirRoot, name, thumbDir);
    }

    /**
     * 装配 {@link StackResult}：优先消费上游 stack Activity 写入的流程变量；缺失时用 {@link MdocReconstructPaths}
     * 派生路径填补。{@code rawFiles} 与 {@code files} 在 cyroems 端通常为同一份输入清单（filter 之前）。
     */
    private static void applyStackResult(
            MdocResult mdocResult,
            DelegateExecution execution,
            MdocReconstructPaths paths) {
        Object filesVar = execution.getVariable("mdocStackFiles");
        List<String> files = (filesVar instanceof List<?> list)
                ? list.stream().map(String::valueOf).toList()
                : null;

        String outputFile = firstNonBlank(
                WorkflowVariableReader.readText(execution, "mdocStackOutputFile"),
                paths.outputMrc());
        String titlFile = firstNonBlank(
                WorkflowVariableReader.readText(execution, "mdocStackTitleFile"),
                paths.outputTitleFile());
        String excludeFile = firstNonBlank(
                WorkflowVariableReader.readText(execution, "mdocStackExcludeFile"),
                paths.excludeFile());

        StackResult stackResult = new StackResult();
        stackResult.setFiles(files);
        stackResult.setRawFiles(files);
        stackResult.setOutputFile(outputFile);
        stackResult.setTitlFile(titlFile);
        stackResult.setExcludeFile(excludeFile);
        mdocResult.setStackResult(stackResult);
    }

    /**
     * 按 {@link MdocReconstructPaths} 派生路径装配 coarse-align / patch-tracking / series-align /
     * align-recon 全部子结果；不做存在性校验（cyroems 同形：路径作为字面持久化，下游消费方自检）。
     */
    private static void applyReconstructResults(MdocResult mdocResult, MdocReconstructPaths paths) {
        mdocResult.setCoarseAlignrResult(new CoarseAlignrResult(
                paths.prexf(),
                paths.prexg(),
                paths.preali()));

        mdocResult.setPatchTrackingResult(new PatchTrackingResult(
                paths.ptFid(),
                paths.fid()));

        mdocResult.setSeriesAlignResult(new SeriesAlignResult(
                paths.threeDmod(),
                paths.resid(),
                paths.fidXyz(),
                paths.tlt(),
                paths.xtilt(),
                paths.tltxf(),
                paths.nogapsFid()));

        mdocResult.setAlignReconResult(new AlignReconResult(
                paths.fidXf(),
                paths.resmod(),
                paths.ali(),
                paths.aliBin1(),
                paths.fullRec(),
                paths.fullRec(), // binvol 原位覆盖 tilt 输出
                paths.thumb(),
                paths.thumbXY(),
                paths.thumbYZ(),
                paths.thumbXZ()));
    }

    /** 与 cyroems 一致：把 align_recon 缩略图作为 {@code mdoc_recon} 类型的 {@link MovieImage} 写入 images。 */
    private static void applyMdocReconImage(MdocResult mdocResult, MdocReconstructPaths paths) {
        if (StringUtils.hasText(paths.thumb())) {
            mdocResult.addImage(new MovieImage(MovieImage.Type.mdoc_recon, paths.thumb()));
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
