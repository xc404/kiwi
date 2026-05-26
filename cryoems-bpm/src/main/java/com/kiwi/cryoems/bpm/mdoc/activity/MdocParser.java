package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.support.MdocFileParser;
import com.kiwi.cryoems.bpm.support.WorkflowVariableReader;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

/**
 * cryoems mdoc 解析活动：从流程变量读取 mdoc 文件路径，调用 {@link MdocFileParser} 得到 {@link MdocMeta}
 * 并写回流程变量，供后续 stack / coarse-align / motion-wait 等节点消费。
 *
 * <p>结构与职责对齐 movie 包内的 {@code CryoemsPrepareActivity}：activity 仅负责 Camunda 入参/出参与
 * 错误码转换，重活交给 {@link MdocFileParser}。</p>
 */
@ComponentDescription(
        name = "CryoEMS Mdoc 解析",
        group = "CryoEM",
        version = "1.0",
        description =
                "解析 SerialEM/IMOD 输出的 *.mrc.mdoc 文本，写出 MdocMeta 流程变量；"
                        + "mdoc 路径来自 mdocFile，或流程变量 mdoc 中的 path / file_path / filePath。",
        inputs = {
                @ComponentParameter(
                        key = "mdocFile",
                        name = "mdoc 文件路径",
                        description = "mdoc 文本文件绝对路径（字符串）",
                        required = true),
                @ComponentParameter(
                        key = "mdoc_data_id",
                        name = "mdoc data Id",
                        required = true)
        },
        outputs = {
                @ComponentParameter(
                        key = "mdocMeta",
                        name = "mdocMeta",
                        description =
                                "MdocMeta 头部字段（不含 rawTiltMetas / tilts）写入的流程变量名",
                        schema = @Schema(defaultValue = "mdocMeta"))
        })
@Component("cyroemsMdocParserActivity")
@RequiredArgsConstructor
@Slf4j
public class MdocParser implements JavaDelegate {

    private final MdocFileParser mdocFileParser;
    private final MDocRepository mDocRepository;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch (BpmnError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BpmnError("MDOC_PARSE_INPUT", e.getMessage(), e);
        } catch (Exception e) {
            throw new BpmnError("MDOC_PARSE", "mdoc 解析失败: " + e.getMessage(), e);
        }
    }

    private void doExecute(DelegateExecution execution) {
        String mdocDataId = ExecutionUtils.getStringInputVariable(execution, "mdoc_data_id").orElseThrow(() -> new IllegalArgumentException("流程变量 mdoc_data_id 不能为空"));
        MDoc mDoc = this.mDocRepository.findById(mdocDataId).orElseThrow();
        MdocMeta meta = mDoc.getMeta();
        if(mDoc.getMeta() == null){
            String mdocFile = resolveMdocFile(execution);
            log.info("开始解析 mdoc: {}", mdocFile);
            meta = mdocFileParser.parse(new File(mdocFile));
            mDoc.setMeta(meta);
            this.mDocRepository.save(mDoc);
        }
        execution.setVariable("mdocMeta", toWorkflowMeta(meta));
        log.info(
                "mdoc 解析完成: tilts={}, binning={}, spotSize={}, voltage={}",
                meta.getTilts() == null ? 0 : meta.getTilts().size(),
                meta.getBinning(),
                meta.getSpotSize(),
                meta.getVoltage());
    }

    /** 仅保留 header 级字段，避免将 tilt 列表序列化进 Camunda 流程变量。 */
    private MdocMeta toWorkflowMeta(MdocMeta source) {
        MdocMeta target = new MdocMeta();
        target.setDataMode(source.getDataMode());
        target.setImageSize(source.getImageSize());
        target.setImageFile(source.getImageFile());
        target.setPixelSpacing(source.getPixelSpacing());
        target.setTomography(source.getTomography());
        target.setTiltAxisAngle(source.getTiltAxisAngle());
        target.setBinning(source.getBinning());
        target.setSpotSize(source.getSpotSize());
        target.setVoltage(source.getVoltage());
        return target;
    }

    /**
     * 解析 mdoc 文件路径：优先取顶层 {@code mdocFile}；缺失时回退到嵌套 {@code mdoc.path} /
     * {@code mdoc.file_path} / {@code mdoc.filePath}，与 movie 端 {@code resolveMovieFile} 同形。
     */
    private static String resolveMdocFile(DelegateExecution execution) {
        String direct = WorkflowVariableReader.readText(execution, "mdocFile");
        if (direct != null) {
            return direct;
        }
        Object mdoc = execution.getVariable("mdoc");
        if (mdoc instanceof Map<?, ?> m) {
            for (String key : new String[] {"path", "file_path", "filePath"}) {
                Object value = m.get(key);
                if (value == null) {
                    continue;
                }
                String text = value.toString().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        throw new IllegalArgumentException("需要流程变量 mdocFile（字符串）或 mdoc.path / file_path / filePath");
    }
}
