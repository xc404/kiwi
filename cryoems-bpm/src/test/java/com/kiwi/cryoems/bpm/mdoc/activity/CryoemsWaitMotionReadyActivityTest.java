package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CryoemsWaitMotionReadyActivityTest {

    private MDocRepository mDocRepository;
    private MovieResultRepository movieResultRepository;
    private CryoemsWaitMotionReadyActivity activity;

    @BeforeEach
    void setUp() {
        mDocRepository = mock(MDocRepository.class);
        movieResultRepository = mock(MovieResultRepository.class);
        activity = new CryoemsWaitMotionReadyActivity(mDocRepository, movieResultRepository);
    }

    @Test
    void execute_setsAllReadyTrueWhenAllTiltsHaveMotion() {
        String mdocDataId = "mdoc-1";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", null),
                tilt("tilt-B", null));
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
    }

    @Test
    void execute_setsAllReadyFalseWhenAnyTiltMissingMotion() {
        String mdocDataId = "mdoc-2";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", null),
                tilt("tilt-B", null));
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
    void execute_setsAllReadyFalseWhenTiltDataIdMissing() {
        String mdocDataId = "mdoc-3";
        String configId = "cfg-1";

        MDoc mDoc = mDocWithTilts(mdocDataId,
                tilt("tilt-A", null),
                tilt(null, null));
        when(mDocRepository.findById(eq(mdocDataId))).thenReturn(Optional.of(mDoc));

        Map<String, Object> vars = baseVars(mdocDataId, configId);
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("all_motion_ready")).isEqualTo(false);
        assertThat(vars.get("motion_ready_count")).isEqualTo(0);
        assertThat(vars.get("motion_total_count")).isEqualTo(2);
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
    }

    @Test
    void execute_backfillsMotionResultIdAndSavesMdoc() {
        String mdocDataId = "mdoc-4";
        String configId = "cfg-1";

        MdocTiltMeta tA = tilt("tilt-A", null);
        MdocTiltMeta tB = tilt("tilt-B", "stale-mr-B"); // 已有旧的 → 也应被覆盖
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
                tilt("tilt-A", "mr-A"),
                tilt("tilt-B", "mr-B"));
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

    private static MdocTiltMeta tilt(String dataId, String motionResultId) {
        MdocTiltMeta t = new MdocTiltMeta();
        t.setDataId(dataId);
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
        return execution;
    }
}
