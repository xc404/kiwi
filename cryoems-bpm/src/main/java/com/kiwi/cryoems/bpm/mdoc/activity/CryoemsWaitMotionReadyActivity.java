package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * mdoc 流水线"等所有 tilt motion 就绪"判定节点：BPMN 内部 readiness 检查，配合排他网关 + timer
 * 实现自循环轮询，替代原本由 cryo-em-server-backend 端 {@code MdocMotionWaitScheduler} 定时扫描
 * 并主动调 Kiwi REST {@code complete} 推动 manualTask 的跨服务做法。
 *
 * <p>核心步骤（参考 {@code MdocMotionWaitScheduler.checkMotionWaitReady}）：
 * <ol>
 *     <li>按 {@code mdoc_dataId} 加载 {@link MDoc}；空或 {@code meta.tilts} 空 → 未就绪；</li>
 *     <li>收集所有 {@link MdocTiltMeta#getDataId()}；任一为空 → 未就绪（不做 dataset 绑定 bootstrap，
 *         那是 cryo 后端的职责）；</li>
 *     <li>按 {@code movie_data_id IN tiltDataIds AND config_id = task_config_id} 查 {@link MovieResult}；
 *         按 movie_data_id 分组取最新（{@link MovieResult#pickNewer}）；</li>
 *     <li>统计 {@code motion.predict_dose} 非空的条数；不足 tilts 总数 → 未就绪；</li>
 *     <li>全就绪：把每个 tilt 对应 {@link MovieResult#getId()} 回写到 {@link MdocTiltMeta#setMotionResultId}
 *         并保存 mdoc；输出 {@code selectedTiltIds = mDoc.movie_data_ids} 供下游使用。</li>
 * </ol>
 *
 * <p>与 {@code MdocMotionWaitScheduler} 的关键差异：
 * <ul>
 *     <li>不做 {@code ensureMovieLoaded}（按 dataset_id + name 绑定 MovieDataset）——那是上游 bootstrap，
 *         BPM 端只负责"等就绪"；tilt dataId 缺失时直接当未就绪让 BPMN 继续轮询；</li>
 *     <li>不调 {@code KiwiWorkflowClient.complete}——节点自身就是 BPMN 内部，由引擎自然推进；</li>
 *     <li>不依赖 {@code Task} 对象——{@code config_id} 由流程变量 {@code task_config_id} 注入；</li>
 *     <li>不依赖 {@code MDocInstance.currentActivity} / {@code external_workflow_instance_id}——这些是
 *         外部扫描器才需要的索引字段。</li>
 * </ul>
 *
 * <p>BPMN 模板使用模式（不在本类范围）：
 * <pre>{@code
 *   serviceTask(cyroemsWaitMotionReadyActivity)
 *     -> exclusiveGateway
 *       -> [${all_motion_ready == true}] 继续后续节点
 *       -> [default] timerIntermediateCatchEvent(PT30S) -> 回到 serviceTask
 * }</pre>
 */
@ComponentDescription(
        name = "CryoEMS 等待 Motion 就绪",
        group = "CryoEM",
        version = "1.0",
        description = "检查当前 mdoc 所有 tilt 对应 MovieResult.motion.predict_dose 是否全部就绪；"
                + "就绪时设置 all_motion_ready=true 并回写每个 tilt 的 motionResultId、输出 selectedTiltIds。"
                + "配合排他网关 + timer intermediateCatchEvent 实现轮询等待。",
        inputs = {
                @ComponentParameter(
                        key = "mdoc_id",
                        name = "MDocInstance id",
                        description = "MDocInstance 主键；用于日志，readiness 判定本身不依赖",
                        required = true),
                @ComponentParameter(
                        key = "mdoc_dataId",
                        name = "MDoc id",
                        description = "MDoc 主键，用于加载 tilts",
                        required = true),
                @ComponentParameter(
                        key = "task_config_id",
                        name = "task_config_id",
                        description = "MovieResult 查询的 config_id 过滤条件",
                        required = true)
        },
        outputs = {
                @ComponentParameter(
                        key = "all_motion_ready",
                        name = "all_motion_ready",
                        description = "所有 tilt 的 motion.predict_dose 是否全部就绪",
                        schema = @Schema(defaultValue = "all_motion_ready")),
                @ComponentParameter(
                        key = "motion_ready_count",
                        name = "motion_ready_count",
                        description = "已就绪的 tilt 数量（诊断用）",
                        schema = @Schema(defaultValue = "motion_ready_count")),
                @ComponentParameter(
                        key = "motion_total_count",
                        name = "motion_total_count",
                        description = "tilt 总数",
                        schema = @Schema(defaultValue = "motion_total_count")),
                @ComponentParameter(
                        key = "selectedTiltIds",
                        name = "selectedTiltIds",
                        description = "就绪时 = mDoc.movie_data_ids，供下游 stack 等节点使用；未就绪时不写入",
                        schema = @Schema(defaultValue = "selectedTiltIds"))
        })
@Component("cyroemsWaitMotionReadyActivity")
@RequiredArgsConstructor
@Slf4j
public class CryoemsWaitMotionReadyActivity implements JavaDelegate {

    private final MDocRepository mDocRepository;
    private final MovieResultRepository movieResultRepository;

    @Override
    public void execute(DelegateExecution execution) {
        try {
            doExecute(execution);
        } catch (BpmnError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BpmnError("MOTION_WAIT_INPUT", e.getMessage(), e);
        } catch (Exception e) {
            throw new BpmnError("MOTION_WAIT", "等待 motion 就绪检查失败: " + e.getMessage(), e);
        }
    }

    private void doExecute(DelegateExecution execution) {
        String instanceId = ExecutionUtils.getStringInputVariable(execution, "mdoc_id")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalArgumentException("流程变量 mdoc_id 不能为空"));
        String mdocDataId = ExecutionUtils.getStringInputVariable(execution, "mdoc_dataId")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalArgumentException("流程变量 mdoc_dataId 不能为空"));
        String configId = ExecutionUtils.getStringInputVariable(execution, "task_config_id")
                .filter(StringUtils::hasText)
                .orElseThrow(() -> new IllegalArgumentException("流程变量 task_config_id 不能为空"));

        MDoc mDoc = mDocRepository
                .findById(mdocDataId)
                .orElseThrow(() -> new IllegalArgumentException("MDoc 不存在: " + mdocDataId));

        MdocMeta meta = mDoc.getMeta();
        List<MdocTiltMeta> tilts = meta == null || meta.getTilts() == null
                ? Collections.emptyList()
                : meta.getTilts();

        int totalCount = tilts.size();
        execution.setVariable("motion_total_count", totalCount);

        if (totalCount == 0) {
            log.debug("MotionWait not ready: instance {} mdoc {} has no tilts", instanceId, mdocDataId);
            writeNotReady(execution, 0, totalCount);
            return;
        }

        // 收集 tilt.dataId；任一缺失视为未就绪（不做 ensureMovieLoaded bootstrap）
        List<String> tiltDataIds = new ArrayList<>(tilts.size());
        boolean dataIdMissing = false;
        for (MdocTiltMeta t : tilts) {
            if (t == null || !StringUtils.hasText(t.getDataId())) {
                dataIdMissing = true;
                continue;
            }
            tiltDataIds.add(t.getDataId());
        }
        if (dataIdMissing) {
            log.debug(
                    "MotionWait not ready: instance {} mdoc {} has tilt(s) without dataId ({}/{} bound)",
                    instanceId, mdocDataId, tiltDataIds.size(), totalCount);
            writeNotReady(execution, 0, totalCount);
            return;
        }

        Query query = Query.query(
                Criteria.where("movie_data_id").in(tiltDataIds)
                        .and("config_id").is(configId));
        List<MovieResult> movieResults = movieResultRepository.findBy(query);
        Map<String, MovieResult> movieResultByDataId = movieResults.stream()
                .collect(Collectors.toMap(
                        MovieResult::getMovie_data_id,
                        Function.identity(),
                        MovieResult::pickNewer));

        long readyCount = movieResultByDataId.values().stream()
                .filter(m -> Optional.ofNullable(m.getMotion())
                        .map(motion -> motion.getPredict_dose())
                        .isPresent())
                .count();

        if (readyCount < totalCount) {
            log.debug(
                    "MotionWait not ready: instance {} mdoc {} ready={}/{}",
                    instanceId, mdocDataId, readyCount, totalCount);
            writeNotReady(execution, (int) readyCount, totalCount);
            return;
        }

        // 全部就绪：回写 motionResultId
        boolean motionResultIdChanged = false;
        for (MdocTiltMeta t : tilts) {
            MovieResult mr = movieResultByDataId.get(t.getDataId());
            if (mr == null || !StringUtils.hasText(mr.getId())) {
                continue;
            }
            if (!mr.getId().equals(t.getMotionResultId())) {
                t.setMotionResultId(mr.getId());
                motionResultIdChanged = true;
            }
        }
        if (motionResultIdChanged) {
            mDocRepository.save(mDoc);
        }

        execution.setVariable("all_motion_ready", true);
        execution.setVariable("motion_ready_count", (int) readyCount);
        execution.setVariable("selectedTiltIds", mDoc.getMovie_data_ids());

        log.info(
                "MotionWait ready: instance {} mdoc {} tilts={} motionResultIdChanged={}",
                instanceId, mdocDataId, totalCount, motionResultIdChanged);
    }

    private static void writeNotReady(DelegateExecution execution, int readyCount, int totalCount) {
        execution.setVariable("all_motion_ready", false);
        execution.setVariable("motion_ready_count", readyCount);
        execution.setVariable("motion_total_count", totalCount);
    }
}
