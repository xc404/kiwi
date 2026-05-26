package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import com.kiwi.cryoems.bpm.movie.dao.MovieDataSetRepository;
import com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository;
import com.kiwi.cryoems.bpm.movie.model.MovieDataset;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.bson.types.ObjectId;
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
import java.util.concurrent.TimeUnit;
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
 *     <li>若任一 {@link MdocTiltMeta#getDataId()} 缺失，先按
 *         {@code mDoc.belonging_data + tilt.name} 反查 {@link MovieDataset} 集合做
 *         <em>ensureMovieLoaded</em> bootstrap：全部命中则把 {@code MovieDataset.id} 回填到
 *         {@link MdocTiltMeta#setDataId(String)} 并初始化 {@link MDoc#setMovie_data_ids(List)}；
 *         任一缺失则视为未就绪让 BPMN 继续轮询；</li>
 *     <li>按 {@code movie_data_id IN tiltDataIds AND config_id = task_config_id} 查 {@link MovieResult}；
 *         按 movie_data_id 分组取最新（{@link MovieResult#pickNewer}）；</li>
 *     <li>统计 {@code motion.predict_dose} 非空的条数；不足 tilts 总数 → 未就绪；</li>
 *     <li>全就绪：把每个 tilt 对应 {@link MovieResult#getId()} 回写到 {@link MdocTiltMeta#setMotionResultId}
 *         并保存 mdoc；输出 {@code selectedTiltIds = mDoc.movie_data_ids} 供下游使用。</li>
 * </ol>
 *
 * <p>与 {@code MdocMotionWaitScheduler} 的关键差异：
 * <ul>
 *     <li>{@code dataset_id} 不再从 {@code Task.taskSettings.dataset_id} 取，而是直接读
 *         {@link MDoc#getBelonging_data()}——避免 BPM 模块反查 Task 实体；</li>
 *     <li>不调 {@code KiwiWorkflowClient.complete}——节点自身就是 BPMN 内部，由引擎自然推进；</li>
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
 *
 * <p>超时：首次进入轮询时写入 {@code motion_wait_started_at}（epoch 毫秒）；每次执行若
 * {@code now - motion_wait_started_at >= motion_wait_timeout_seconds}（默认 7200，即 2 小时）
 * 且仍未就绪，则抛出 {@code MOTION_WAIT_TIMEOUT}，避免网关 + 定时器无限自循环。
 */
@ComponentDescription(
        name = "CryoEMS 等待 Motion 就绪",
        group = "CryoEM",
        version = "1.0",
        description = "检查当前 mdoc 所有 tilt 对应 MovieResult.motion.predict_dose 是否全部就绪："
                + "若 tilt.dataId 缺失，先按 mDoc.belonging_data + tilt.name 反查 MovieDataset 做绑定；"
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
                        description = "MDoc 主键，用于加载 tilts、belonging_data",
                        required = true),
                @ComponentParameter(
                        key = "task_config_id",
                        name = "task_config_id",
                        description = "MovieResult 查询的 config_id 过滤条件",
                        required = true),
                @ComponentParameter(
                        key = "motion_wait_timeout_seconds",
                        name = "motion_wait_timeout_seconds",
                        description = "等待 motion 就绪的最长秒数，超时抛 MOTION_WAIT_TIMEOUT；默认 7200（2 小时）")
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

    /** 默认等待 motion 就绪超时：2 小时。 */
    private static final long DEFAULT_MOTION_WAIT_TIMEOUT_SECONDS = TimeUnit.HOURS.toSeconds(2);

    private static final String VAR_MOTION_WAIT_STARTED_AT = "motion_wait_started_at";
    private static final String VAR_MOTION_WAIT_TIMEOUT_SECONDS = "motion_wait_timeout_seconds";

    private final MDocRepository mDocRepository;
    private final MovieResultRepository movieResultRepository;
    private final MovieDataSetRepository movieDataSetRepository;

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

        long timeoutSeconds = resolveMotionWaitTimeoutSeconds(execution);
        assertMotionWaitNotTimedOut(execution, instanceId, mdocDataId, timeoutSeconds);

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

        // ensureMovieLoaded bootstrap：tilt.dataId 缺失时按 belonging_data + name 反查 MovieDataset 回填
        if (!ensureMovieLoaded(instanceId, mDoc, tilts)) {
            writeNotReady(execution, 0, totalCount);
            return;
        }

        // ensureMovieLoaded 之后 dataId 应全部就绪；这里再保险地按 dataId 收集
        List<String> tiltDataIds = new ArrayList<>(tilts.size());
        for (MdocTiltMeta t : tilts) {
            if (t != null && StringUtils.hasText(t.getDataId())) {
                tiltDataIds.add(t.getDataId());
            }
        }
        if (tiltDataIds.size() < totalCount) {
            log.debug(
                    "MotionWait not ready: instance {} mdoc {} has tilt(s) without dataId after bind ({}/{})",
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
        execution.removeVariable(VAR_MOTION_WAIT_STARTED_AT);

        log.info(
                "MotionWait ready: instance {} mdoc {} tilts={} motionResultIdChanged={}",
                instanceId, mdocDataId, totalCount, motionResultIdChanged);
    }

    /**
     * 等价于原 {@code MdocMotionWaitScheduler.ensureMovieLoaded}：当 tilt.dataId 仍有缺失时，按
     * {@code mDoc.belonging_data}（替代原来的 task.taskSettings.dataset_id）+ {@code tilt.name}
     * 在 {@link MovieDataset} 集合内匹配，回填 {@link MdocTiltMeta#setDataId(String)} 与
     * {@link MDoc#setMovie_data_ids(List)}，并持久化 mdoc。
     *
     * <p>返回 {@code true}：所有 tilt 的 dataId 已就绪可继续后续 motion 判定；返回 {@code false}：
     * 当前仍有 movie dataset 未入库或元信息缺失，调用方应当未就绪让 BPMN 继续轮询。
     */
    private boolean ensureMovieLoaded(String instanceId, MDoc mDoc, List<MdocTiltMeta> tilts) {
        if(tilts.stream().allMatch(t -> org.apache.commons.lang3.StringUtils.isNotBlank(t.getDataId()))) {
            return true;
        }
        boolean allBound = true;
        List<String> names = new ArrayList<>(tilts.size());
        for (MdocTiltMeta t : tilts) {
            if (t == null) {
                continue;
            }
            if (!StringUtils.hasText(t.getDataId())) {
                allBound = false;
            }
            if (StringUtils.hasText(t.getName())) {
                names.add(FilenameUtils.getBaseName(t.getName()));
            }
        }
        if (allBound) {
            return true;
        }
        String datasetId = mDoc.getBelonging_data();
        if (!StringUtils.hasText(datasetId)) {
            log.warn(
                    "MovieConnect skipped: instance {} mdoc {} has no belonging_data",
                    instanceId, mDoc.getId());
            return false;
        }
        if (names.isEmpty()) {
            log.debug(
                    "MovieConnect skipped: instance {} mdoc {} has no tilt names",
                    instanceId, mDoc.getId());
            return false;
        }
        // belonging_data 在不同写入路径下可能是 ObjectId 或 String，二者都加入 in 条件兼容
        Object datasetIdCriteria;
        try {
            datasetIdCriteria = new ObjectId(datasetId);
        } catch (IllegalArgumentException ex) {
            datasetIdCriteria = datasetId;
        }
        Query q = Query.query(Criteria.where("belonging_data").in(datasetIdCriteria, datasetId))
                .addCriteria(Criteria.where("name").in(names));
        List<MovieDataset> movieDatasets = movieDataSetRepository.findBy(q);
        if (movieDatasets.size() != names.size()) {
            log.debug(
                    "MovieConnect not ready: instance {} mdoc {} matched={}/{}",
                    instanceId, mDoc.getId(), movieDatasets.size(), names.size());
            return false;
        }
        Map<String, MovieDataset> datasetByName = movieDatasets.stream()
                .collect(Collectors.toMap(MovieDataset::getName, Function.identity()));
        for (MdocTiltMeta t : tilts) {
            if (t == null || !StringUtils.hasText(t.getName())) {
                continue;
            }
            String baseName = FilenameUtils.getBaseName(t.getName());
            MovieDataset md = datasetByName.get(baseName);
            if (md == null) {
                log.warn(
                        "MovieConnect inconsistency: instance {} tilt {} has no matched MovieDataset",
                        instanceId, t.getName());
                return false;
            }
            t.setDataId(md.getId());
        }
        if (mDoc.getMovie_data_ids() == null) {
            mDoc.setMovie_data_ids(tilts.stream()
                    .map(MdocTiltMeta::getDataId)
                    .collect(Collectors.toList()));
        }
        mDocRepository.save(mDoc);
        log.info(
                "MovieConnect done: instance {} mdoc {} bound tilts={}",
                instanceId, mDoc.getId(), tilts.size());
        return true;
    }

    private static void writeNotReady(DelegateExecution execution, int readyCount, int totalCount) {
        execution.setVariable("all_motion_ready", false);
        execution.setVariable("motion_ready_count", readyCount);
        execution.setVariable("motion_total_count", totalCount);
    }

    private long resolveMotionWaitTimeoutSeconds(DelegateExecution execution) {
        return ExecutionUtils.getNumberInputVariable(execution, VAR_MOTION_WAIT_TIMEOUT_SECONDS)
                .filter(v -> v > 0)
                .map(Double::longValue)
                .orElse(DEFAULT_MOTION_WAIT_TIMEOUT_SECONDS);
    }

    /**
     * 首次进入等待轮询时记录 {@link #VAR_MOTION_WAIT_STARTED_AT}；后续每次执行在就绪判定前检查是否超时。
     */
    private void assertMotionWaitNotTimedOut(
            DelegateExecution execution,
            String instanceId,
            String mdocDataId,
            long timeoutSeconds) {
        long now = System.currentTimeMillis();
        Long startedAt = readMotionWaitStartedAt(execution);
        if (startedAt == null) {
            execution.setVariable(VAR_MOTION_WAIT_STARTED_AT, now);
            return;
        }
        long elapsedMillis = now - startedAt;
        long timeoutMillis = TimeUnit.SECONDS.toMillis(timeoutSeconds);
        if (elapsedMillis < timeoutMillis) {
            return;
        }
        long elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis);
        String message = String.format(
                "等待 motion 就绪超时（%ds，上限 %ds）：instance=%s mdoc=%s",
                elapsedSeconds, timeoutSeconds, instanceId, mdocDataId);
        log.warn(message);
        throw new BpmnError("MOTION_WAIT_TIMEOUT", message);
    }

    private Long readMotionWaitStartedAt(DelegateExecution execution) {
        Object raw = execution.getVariable(VAR_MOTION_WAIT_STARTED_AT);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
