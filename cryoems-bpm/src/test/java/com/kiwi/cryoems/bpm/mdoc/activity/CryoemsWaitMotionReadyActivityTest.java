package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import com.kiwi.cryoems.bpm.movie.dao.MovieDataSetRepository;
import com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository;
import com.kiwi.cryoems.bpm.movie.model.MovieDataset;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import com.kiwi.cryoems.bpm.movie.model.motion.MotionResult;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CryoemsWaitMotionReadyActivityTest {

    private MDocRepository mDocRepository;
    private MovieResultRepository movieResultRepository;
    private MovieDataSetRepository movieDataSetRepository;
    private CryoemsWaitMotionReadyActivity activity;

    @BeforeEach
    void setUp() {
        mDocRepository = mock(MDocRepository.class);
        movieResultRepository = mock(MovieResultRepository.class);
        movieDataSetRepository = mock(MovieDataSetRepository.class);
        activity = new CryoemsWaitMotionReadyActivity(
                mDocRepository, movieResultRepository, movieDataSetRepository);
    }

    @Test
    void execute_setsAllReadyTrueWhenAllTiltsHaveMotion() {
        String mdocDataId = "mdoc-1";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", "tA-name", null),
                tilt("tilt-B", "tB-name", null));
        mDoc.setMovie_data_ids(Arrays.asList("tilt-A", "tilt-B"));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Arrays.asList(
                        movieResult("mr-A", "tilt-A", configId, 1.0),
                        movieResult("mr-B", "tilt-B", configId, 2.0)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(true);
        assertThat(vars.get("motion_ready_count")).isEqualTo(2);
        assertThat(vars.get("motion_total_count")).isEqualTo(2);
        assertThat(vars.get("selectedTiltIds")).isEqualTo(Arrays.asList("tilt-A", "tilt-B"));
        // 全部 tilt 已有 dataId，不应触发 MovieDataset 反查
        verify(movieDataSetRepository, never()).findBy(any(Query.class));
    }

    @Test
    void execute_setsAllReadyFalseWhenAnyTiltMissingMotion() {
        String mdocDataId = "mdoc-2";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", "tA-name", null),
                tilt("tilt-B", "tB-name", null));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Arrays.asList(
                        movieResult("mr-A", "tilt-A", configId, 1.0),
                        movieResult("mr-B", "tilt-B", configId, null)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(false);
        assertThat(vars.get("motion_ready_count")).isEqualTo(1);
        assertThat(vars.get("motion_total_count")).isEqualTo(2);
        assertThat(vars).doesNotContainKey("selectedTiltIds");
        verify(mDocRepository, never()).save(any(MDoc.class));
    }

    @Test
    void execute_setsAllReadyFalseWhenBelongingDataMissingAndDataIdMissing() {
        String mdocDataId = "mdoc-3";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", "tA-name", null),
                tilt(null, "tB-name", null));
        // belonging_data 缺失，无法做 ensureMovieLoaded bootstrap
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(false);
        assertThat(vars.get("motion_ready_count")).isEqualTo(0);
        assertThat(vars.get("motion_total_count")).isEqualTo(2);
        verify(movieDataSetRepository, never()).findBy(any(Query.class));
        verify(movieResultRepository, never()).findBy(any(Query.class));
    }

    @Test
    void execute_setsAllReadyFalseWhenMdocHasNoTilts() {
        String mdocDataId = "mdoc-empty";
        String configId = "cfg-1";

        MDoc mDoc = new MDoc();
        mDoc.setId(mdocDataId);
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(false);
        assertThat(vars.get("motion_total_count")).isEqualTo(0);
        verify(movieResultRepository, never()).findBy(any(Query.class));
        verify(movieDataSetRepository, never()).findBy(any(Query.class));
    }

    @Test
    void execute_backfillsMotionResultIdAndSavesMdoc() {
        String mdocDataId = "mdoc-4";
        String configId = "cfg-1";

        MdocTiltMeta tA = tilt("tilt-A", "tA-name", null);
        MdocTiltMeta tB = tilt("tilt-B", "tB-name", "stale-mr-B"); // 已有旧的 → 也应被覆盖
        MDoc mDoc = mDocWithTilts(mdocDataId, tA, tB);
        mDoc.setMovie_data_ids(Arrays.asList("tilt-A", "tilt-B"));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Arrays.asList(
                        movieResult("mr-A", "tilt-A", configId, 1.0),
                        movieResult("mr-B", "tilt-B", configId, 2.0)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        ArgumentCaptor<MDoc> captor = ArgumentCaptor.forClass(MDoc.class);
        verify(mDocRepository).save(captor.capture());
        MDoc saved = captor.getValue();
        assertThat(saved.getMeta().getTilts())
                .extracting(MdocTiltMeta::getDataId, MdocTiltMeta::getMotionResultId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("tilt-A", "mr-A"),
                        org.assertj.core.groups.Tuple.tuple("tilt-B", "mr-B"));
        assertThat(vars.get("all_motion_ready")).isEqualTo(true);
    }

    @Test
    void execute_doesNotSaveMdocWhenMotionResultIdsAlreadyMatch() {
        String mdocDataId = "mdoc-5";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", "tA-name", "mr-A"),
                tilt("tilt-B", "tB-name", "mr-B"));
        mDoc.setMovie_data_ids(Arrays.asList("tilt-A", "tilt-B"));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Arrays.asList(
                        movieResult("mr-A", "tilt-A", configId, 1.0),
                        movieResult("mr-B", "tilt-B", configId, 2.0)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        verify(mDocRepository, never()).save(any(MDoc.class));
        assertThat(vars.get("all_motion_ready")).isEqualTo(true);
    }

    @Test
    void execute_ensureMovieLoadedBindsDataIdsThenSetsReady() {
        String mdocDataId = "mdoc-6";
        String configId = "cfg-1";
        String datasetId = "ds-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt(null, "tA-name", null),
                tilt(null, "tB-name", null));
        mDoc.setBelonging_data(datasetId);
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieDataSetRepository.findBy(any(Query.class)))
                .thenReturn(Arrays.asList(
                        movieDataset("ds-tA", "tA-name"),
                        movieDataset("ds-tB", "tB-name")));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Arrays.asList(
                        movieResult("mr-A", "ds-tA", configId, 1.0),
                        movieResult("mr-B", "ds-tB", configId, 2.0)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        // ensureMovieLoaded 完成绑定 + 第二次（motionResultId 回写）合计 mDoc.save 至少 1 次
        verify(mDocRepository, atLeastOnce()).save(any(MDoc.class));
        assertThat(mDoc.getMeta().getTilts())
                .extracting(MdocTiltMeta::getDataId, MdocTiltMeta::getMotionResultId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ds-tA", "mr-A"),
                        org.assertj.core.groups.Tuple.tuple("ds-tB", "mr-B"));
        assertThat(mDoc.getMovie_data_ids()).containsExactly("ds-tA", "ds-tB");
        assertThat(vars.get("all_motion_ready")).isEqualTo(true);
        assertThat(vars.get("selectedTiltIds")).isEqualTo(Arrays.asList("ds-tA", "ds-tB"));
    }

    @Test
    void execute_ensureMovieLoadedNotReadyWhenDatasetPartiallyMatched() {
        String mdocDataId = "mdoc-7";
        String configId = "cfg-1";
        String datasetId = "ds-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt(null, "tA-name", null),
                tilt(null, "tB-name", null));
        mDoc.setBelonging_data(datasetId);
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieDataSetRepository.findBy(any(Query.class)))
                .thenReturn(Collections.singletonList(movieDataset("ds-tA", "tA-name")));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(false);
        assertThat(vars.get("motion_ready_count")).isEqualTo(0);
        assertThat(vars.get("motion_total_count")).isEqualTo(2);
        verify(movieResultRepository, never()).findBy(any(Query.class));
        verify(mDocRepository, never()).save(any(MDoc.class));
    }

    @Test
    void execute_ensureMovieLoadedSkipsWhenAllTiltNamesEmpty() {
        String mdocDataId = "mdoc-8";
        String configId = "cfg-1";
        String datasetId = "ds-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt(null, null, null),
                tilt(null, null, null));
        mDoc.setBelonging_data(datasetId);
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(false);
        verify(movieDataSetRepository, never()).findBy(any(Query.class));
        verify(movieResultRepository, never()).findBy(any(Query.class));
    }

    @Test
    void execute_throwsBpmnErrorWhenMdocIdMissing() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_dataId", "mdoc-x");
        vars.put("task_config_id", "cfg-1");
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("mdoc_id");
    }

    @Test
    void execute_throwsBpmnErrorWhenMdocDataIdMissing() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-x");
        vars.put("task_config_id", "cfg-1");
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("mdoc_dataId");
    }

    @Test
    void execute_throwsBpmnErrorWhenConfigIdMissing() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-x");
        vars.put("mdoc_dataId", "mdoc-x");
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("task_config_id");
    }

    @Test
    void execute_recordsStartedAtOnFirstPollWhenNotReady() {
        String mdocDataId = "mdoc-timeout-1";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", "tA-name", null));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Collections.singletonList(
                        movieResult("mr-A", "tilt-A", configId, null)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        long before = System.currentTimeMillis();
        activity.execute(execution);
        long after = System.currentTimeMillis();

        assertThat(vars.get("all_motion_ready")).isEqualTo(false);
        assertThat(vars).containsKey("motion_wait_started_at");
        long startedAt = ((Number) vars.get("motion_wait_started_at")).longValue();
        assertThat(startedAt).isBetween(before, after);
    }

    @Test
    void execute_throwsMotionWaitTimeoutWhenElapsedExceedsLimit() {
        String mdocDataId = "mdoc-timeout-2";
        String configId = "cfg-1";
        long timeoutSeconds = 7200L;

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", "tA-name", null));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Collections.singletonList(
                        movieResult("mr-A", "tilt-A", configId, null)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        vars.put("motion_wait_timeout_seconds", timeoutSeconds);
        vars.put(
                "motion_wait_started_at",
                System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(timeoutSeconds + 1));
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .extracting(e -> ((BpmnError) e).getErrorCode())
                .isEqualTo("MOTION_WAIT_TIMEOUT");
    }

    @Test
    void execute_clearsStartedAtWhenAllMotionReady() {
        String mdocDataId = "mdoc-timeout-3";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId, tilt("tilt-A", "tA-name", null));
        mDoc.setMovie_data_ids(Collections.singletonList("tilt-A"));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));
        when(movieResultRepository.findBy(any(Query.class)))
                .thenReturn(Collections.singletonList(
                        movieResult("mr-A", "tilt-A", configId, 1.0)));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        vars.put("motion_wait_started_at", System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(true);
        assertThat(vars).doesNotContainKey("motion_wait_started_at");
    }

    @Test
    void execute_throwsBpmnErrorWhenMdocNotFound() {
        when(mDocRepository.findById(anyString())).thenReturn(Optional.empty());

        Map<String, Object> vars = baseVars("mdoc-missing", "cfg-1");
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("MDoc 不存在");
    }

    // --- helpers ------------------------------------------------------------

    private static Map<String, Object> baseVars(String mdocDataId, String configId) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-1");
        vars.put("mdoc_dataId", mdocDataId);
        vars.put("task_config_id", configId);
        return vars;
    }

    private static MdocTiltMeta tilt(String dataId, String name, String motionResultId) {
        MdocTiltMeta t = new MdocTiltMeta();
        t.setDataId(dataId);
        t.setName(name);
        t.setMotionResultId(motionResultId);
        return t;
    }

    private static MDoc mDocWithTilts(String id, MdocTiltMeta... tilts) {
        MDoc mDoc = new MDoc();
        mDoc.setId(id);
        MdocMeta meta = new MdocMeta();
        List<MdocTiltMeta> list = new ArrayList<>(Arrays.asList(tilts));
        meta.setTilts(list);
        mDoc.setMeta(meta);
        return mDoc;
    }

    private static MovieResult movieResult(String id, String movieDataId, String configId, Double predictDose) {
        MovieResult mr = new MovieResult();
        mr.setId(id);
        mr.setMovie_data_id(movieDataId);
        mr.setConfig_id(configId);
        if (predictDose != null) {
            MotionResult motion = new MotionResult();
            motion.setPredict_dose(predictDose);
            mr.setMotion(motion);
        }
        return mr;
    }

    private static MovieDataset movieDataset(String id, String name) {
        MovieDataset md = new MovieDataset();
        md.setId(id);
        md.setName(name);
        return md;
    }

    private static DelegateExecution stubExecution(Map<String, Object> vars) {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable(anyString())).thenAnswer(inv -> vars.get((String) inv.getArgument(0)));
        when(execution.getVariableTyped(anyString())).thenAnswer(inv -> {
            Object value = vars.get((String) inv.getArgument(0));
            if (value == null) {
                return null;
            }
            return Variables.objectValue(value).create();
        });
        doAnswer(inv -> {
            vars.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(execution).setVariable(anyString(), any());
        doAnswer(inv -> {
            vars.remove(inv.getArgument(0));
            return null;
        }).when(execution).removeVariable(anyString());
        return execution;
    }
}
