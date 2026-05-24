package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.cryoems.bpm.mdoc.dao.MDocInstanceRepository;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.dao.MdocResultRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MDocInstance;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocResult;
import com.kiwi.cryoems.bpm.mdoc.support.MdocPathResolver;
import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Path;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CryoemsCreateMdocResultActivityTest {

    private MDocRepository mDocRepository;
    private MDocInstanceRepository mDocInstanceRepository;
    private MdocResultRepository mdocResultRepository;
    private CryoemsCreateMdocResultActivity activity;

    @BeforeEach
    void setUp() {
        mDocRepository = mock(MDocRepository.class);
        mDocInstanceRepository = mock(MDocInstanceRepository.class);
        mdocResultRepository = mock(MdocResultRepository.class);
        activity = new CryoemsCreateMdocResultActivity(
                mDocRepository,
                mDocInstanceRepository,
                mdocResultRepository,
                new MdocPathResolver());
    }

    @Test
    void execute_persistsAllSubResultsDerivedFromMdocPaths(@TempDir File workRoot) {
        MdocMeta meta = new MdocMeta();
        meta.setVoltage(300.0);

        MDoc mDoc = new MDoc();
        mDoc.setId("mdoc-1");
        mDoc.setMeta(meta);

        MDocInstance instance = new MDocInstance();
        instance.setId("inst-1");
        instance.setName("Position_6_3");
        instance.setTask_id("task-fallback");

        when(mDocRepository.findById(eq("mdoc-1"))).thenReturn(Optional.of(mDoc));
        when(mDocInstanceRepository.findById(eq("inst-1"))).thenReturn(Optional.of(instance));
        when(mdocResultRepository.save(any(MdocResult.class))).thenAnswer(inv -> {
            MdocResult r = inv.getArgument(0);
            r.setId("result-generated");
            return r;
        });

        Path mdocWorkDir = Path.of(workRoot.getAbsolutePath(), "mdoc", "Position_6_3")
                .toAbsolutePath().normalize();
        Path thumbDir = Path.of(workRoot.getAbsolutePath(), "thumbnails")
                .toAbsolutePath().normalize();

        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-1");
        vars.put("mdoc_dataId", "mdoc-1");
        vars.put("task_id", "task-1");
        vars.put("task_dataId", "task-data-1");
        vars.put("task_config_id", "cfg-1");
        vars.put("category", "default");
        vars.put("work_dir", workRoot.getAbsolutePath());
        vars.put("mdocStackWorkDir", mdocWorkDir.toString());
        vars.put("mdocStackFiles", List.of("/data/a.mrc", "/data/b.mrc"));
        vars.put("mdocStackOutputFile", mdocWorkDir.resolve("Position_6_3_raw_bin.mrc").toString());
        vars.put("mdocStackTitleFile", mdocWorkDir.resolve("Position_6_3_raw_bin.rawtilt").toString());
        vars.put("mdocStackExcludeFile", mdocWorkDir.resolve("Position_6_3_raw_bin_files.txt").toString());

        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        ArgumentCaptor<MdocResult> captor = ArgumentCaptor.forClass(MdocResult.class);
        verify(mdocResultRepository).save(captor.capture());
        MdocResult saved = captor.getValue();

        // 基础键
        assertThat(saved.getInstance_id()).isEqualTo("inst-1");
        assertThat(saved.getData_id()).isEqualTo("mdoc-1");
        assertThat(saved.getTask_id()).isEqualTo("task-1");
        assertThat(saved.getTask_data_id()).isEqualTo("task-data-1");
        assertThat(saved.getConfig_id()).isEqualTo("cfg-1");
        assertThat(saved.getCategory()).isEqualTo("default");
        assertThat(saved.getRate()).isZero();
        assertThat(saved.getMeta()).isSameAs(meta);

        // stack
        assertThat(saved.getStackResult()).isNotNull();
        assertThat(saved.getStackResult().getFiles()).containsExactly("/data/a.mrc", "/data/b.mrc");
        assertThat(saved.getStackResult().getRawFiles()).containsExactly("/data/a.mrc", "/data/b.mrc");
        assertThat(saved.getStackResult().getOutputFile())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_raw_bin.mrc").toString());
        assertThat(saved.getStackResult().getTitlFile())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_raw_bin.rawtilt").toString());
        assertThat(saved.getStackResult().getExcludeFile())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_raw_bin_files.txt").toString());

        // coarse-align
        assertThat(saved.getCoarseAlignrResult()).isNotNull();
        assertThat(saved.getCoarseAlignrResult().getTiltxcorrOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.prexf").toString());
        assertThat(saved.getCoarseAlignrResult().getXftoxgOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.prexg").toString());
        assertThat(saved.getCoarseAlignrResult().getNewstackOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_preali.mrc").toString());

        // patch-tracking
        assertThat(saved.getPatchTrackingResult()).isNotNull();
        assertThat(saved.getPatchTrackingResult().getTiltxcorrOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_pt.fid").toString());
        assertThat(saved.getPatchTrackingResult().getImodchopcontsOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.fid").toString());

        // series-align
        assertThat(saved.getSeriesAlignResult()).isNotNull();
        assertThat(saved.getSeriesAlignResult().getModelFileOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.3dmod").toString());
        assertThat(saved.getSeriesAlignResult().getResidualFileOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.resid").toString());
        // 注意 cyroems 命名特殊：name 与 "fid" 直连无下划线
        assertThat(saved.getSeriesAlignResult().getFidXYZOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3fid.xyz").toString());
        assertThat(saved.getSeriesAlignResult().getTiltFileOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.tlt").toString());
        assertThat(saved.getSeriesAlignResult().getXAxisTiltOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.xtilt").toString());
        assertThat(saved.getSeriesAlignResult().getTransformOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.tltxf").toString());
        assertThat(saved.getSeriesAlignResult().getFilledInModelOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_nogaps.fid").toString());

        // align-recon
        assertThat(saved.getAlignReconResult()).isNotNull();
        assertThat(saved.getAlignReconResult().getXfproductOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_fid.xf").toString());
        assertThat(saved.getAlignReconResult().getPatch2imodOutput())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3.resmod").toString());
        assertThat(saved.getAlignReconResult().getStack1Output())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_ali.mrc").toString());
        assertThat(saved.getAlignReconResult().getStack2Output())
                .isEqualTo(mdocWorkDir.resolve("Position_6_3_ali_bin1.mrc").toString());
        // tilt 与 binvol 同指（binvol 原位覆盖）
        String fullRec = mdocWorkDir.resolve("Position_6_3_full_rec_bin4.mrc").toString();
        assertThat(saved.getAlignReconResult().getTiltOutput()).isEqualTo(fullRec);
        assertThat(saved.getAlignReconResult().getBinvolOutput()).isEqualTo(fullRec);
        // thumbnails
        String thumbBase = "Position_6_3_full_rec_bin8_unit8";
        assertThat(saved.getAlignReconResult().getAlign_reconOutput())
                .isEqualTo(thumbDir.resolve(thumbBase + ".mrc").toString());
        assertThat(saved.getAlignReconResult().getAlign_recon_x_y_view())
                .isEqualTo(thumbDir.resolve(thumbBase + "_xy.png").toString());
        assertThat(saved.getAlignReconResult().getAlign_recon_y_z_view())
                .isEqualTo(thumbDir.resolve(thumbBase + "_yz.png").toString());
        assertThat(saved.getAlignReconResult().getAlign_recon_x_z_view())
                .isEqualTo(thumbDir.resolve(thumbBase + "_xz.png").toString());

        // images.mdoc_recon
        assertThat(saved.getImages()).containsKey(MovieImage.Type.mdoc_recon);
        assertThat(saved.getImages().get(MovieImage.Type.mdoc_recon).getPath())
                .isEqualTo(thumbDir.resolve(thumbBase + ".mrc").toString());

        // 输出流程变量
        assertThat(vars.get("mdocResultId")).isEqualTo("result-generated");
        assertThat(vars.get("mdocResult")).isInstanceOf(MdocResult.class);
    }

    @Test
    void execute_resolvesPathsFromWorkDirWhenMdocStackWorkDirAbsent(@TempDir File workRoot) {
        MDoc mDoc = new MDoc();
        mDoc.setId("mdoc-2");

        MDocInstance instance = new MDocInstance();
        instance.setId("inst-2");
        instance.setName("tomo_B");
        instance.setTask_id("task-from-instance");

        when(mDocRepository.findById(eq("mdoc-2"))).thenReturn(Optional.of(mDoc));
        when(mDocInstanceRepository.findById(eq("inst-2"))).thenReturn(Optional.of(instance));
        when(mdocResultRepository.save(any(MdocResult.class))).thenAnswer(inv -> {
            MdocResult r = inv.getArgument(0);
            r.setId("result-2");
            return r;
        });

        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-2");
        vars.put("mdoc_dataId", "mdoc-2");
        vars.put("work_dir", workRoot.getAbsolutePath());
        // 不显式 task_* / category / mdocStackWorkDir / mdocStackFiles
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        ArgumentCaptor<MdocResult> captor = ArgumentCaptor.forClass(MdocResult.class);
        verify(mdocResultRepository).save(captor.capture());
        MdocResult saved = captor.getValue();

        assertThat(saved.getTask_id()).isEqualTo("task-from-instance");
        assertThat(saved.getTask_data_id()).isNull();
        assertThat(saved.getConfig_id()).isNull();
        assertThat(saved.getCategory()).isEqualTo("default");

        Path mdocWorkDir = Path.of(workRoot.getAbsolutePath(), "mdoc", "tomo_B")
                .toAbsolutePath().normalize();
        Path thumbDir = Path.of(workRoot.getAbsolutePath(), "thumbnails")
                .toAbsolutePath().normalize();

        // 上游变量缺失时，stack 路径全部退回派生
        assertThat(saved.getStackResult().getOutputFile())
                .isEqualTo(mdocWorkDir.resolve("tomo_B_raw_bin.mrc").toString());
        assertThat(saved.getStackResult().getTitlFile())
                .isEqualTo(mdocWorkDir.resolve("tomo_B_raw_bin.rawtilt").toString());
        assertThat(saved.getStackResult().getFiles()).isNull();
        assertThat(saved.getCoarseAlignrResult().getNewstackOutput())
                .isEqualTo(mdocWorkDir.resolve("tomo_B_preali.mrc").toString());
        assertThat(saved.getAlignReconResult().getAlign_reconOutput())
                .isEqualTo(thumbDir.resolve("tomo_B_full_rec_bin8_unit8.mrc").toString());
    }

    @Test
    void execute_throwsBpmnErrorWhenMdocIdMissing() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_dataId", "mdoc-x");
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("mdoc_id");
    }

    @Test
    void execute_throwsBpmnErrorWhenMdocNotFound() {
        when(mDocRepository.findById(anyString())).thenReturn(Optional.empty());

        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-x");
        vars.put("mdoc_dataId", "mdoc-x");
        vars.put("work_dir", "/tmp");
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("MDoc 不存在");
    }

    @Test
    void execute_throwsWhenWorkDirAndStackWorkDirBothMissing() {
        MDoc mDoc = new MDoc();
        mDoc.setId("mdoc-w");
        MDocInstance instance = new MDocInstance();
        instance.setId("inst-w");
        instance.setName("tomo_W");
        when(mDocRepository.findById(eq("mdoc-w"))).thenReturn(Optional.of(mDoc));
        when(mDocInstanceRepository.findById(eq("inst-w"))).thenReturn(Optional.of(instance));

        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-w");
        vars.put("mdoc_dataId", "mdoc-w");
        DelegateExecution execution = stubExecution(vars);

        assertThatThrownBy(() -> activity.execute(execution))
                .isInstanceOf(BpmnError.class)
                .hasMessageContaining("work_dir");
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
