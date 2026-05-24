package com.kiwi.cryoems.bpm.mdoc.activity;

import com.kiwi.cryoems.bpm.mdoc.dao.MDocInstanceRepository;
import com.kiwi.cryoems.bpm.mdoc.dao.MDocRepository;
import com.kiwi.cryoems.bpm.mdoc.model.MDoc;
import com.kiwi.cryoems.bpm.mdoc.model.MDocInstance;
import com.kiwi.cryoems.bpm.mdoc.model.MdocMeta;
import com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta;
import com.kiwi.cryoems.bpm.mdoc.support.MdocPathResolver;
import com.kiwi.cryoems.bpm.mdoc.support.MdocStackInputBuilder;
import com.kiwi.cryoems.bpm.mdoc.support.MdocStackInputBuilder.MdocStackInputs;
import com.kiwi.cryoems.bpm.movie.dao.MovieResultRepository;
import com.kiwi.cryoems.bpm.movie.model.MovieResult;
import com.kiwi.cryoems.bpm.movie.model.motion.MotionResult;
import com.kiwi.cryoems.bpm.movie.model.motion.MrcFile;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CryoemsMdocStackActivityTest {

    private MDocRepository mDocRepository;
    private MDocInstanceRepository mDocInstanceRepository;
    private MovieResultRepository movieResultRepository;
    private MdocStackInputBuilder inputBuilder;
    private CryoemsMdocStackActivity activity;

    @BeforeEach
    void setUp() throws Exception {
        mDocRepository = mock(MDocRepository.class);
        mDocInstanceRepository = mock(MDocInstanceRepository.class);
        movieResultRepository = mock(MovieResultRepository.class);
        inputBuilder = new MdocStackInputBuilder(
                mDocRepository, mDocInstanceRepository, movieResultRepository, new MdocPathResolver());
        activity = spy(new CryoemsMdocStackActivity(inputBuilder));
        ReflectionTestUtils.setField(activity, "stackCommand", "mdoc_stack");
        ReflectionTestUtils.setField(activity, "timeoutSeconds", 60L);
        // 默认打桩同步执行，避免 CI 真正启动 conda / mrc_stack.py。
        doNothing().when(activity).runStack(any(MdocStackInputs.class));
    }

    @Test
    void execute_writesScriptInvokesMdocStackAndProcessVariables(@TempDir File workRoot) throws Exception {
        MDoc mDoc = new MDoc();
        mDoc.setId("mdoc-1");
        mDoc.setManualRebuild(true);
        MdocMeta meta = new MdocMeta();
        meta.setTilts(List.of(
                tilt("data-1", "mid-1", -10.0),
                tilt("data-2", "mid-2", 10.0)));
        mDoc.setMeta(meta);

        MDocInstance instance = new MDocInstance();
        instance.setId("inst-1");
        instance.setName("tomo_A");

        when(mDocRepository.findById(eq("mdoc-1"))).thenReturn(Optional.of(mDoc));
        when(mDocInstanceRepository.findById(eq("inst-1"))).thenReturn(Optional.of(instance));
        when(movieResultRepository.findAllById(List.of("mid-1", "mid-2")))
                .thenReturn(List.of(
                        movieResult("mid-1", "data-1", "/data/a.mrc"),
                        movieResult("mid-2", "data-2", "/data/b.mrc")));

        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", "inst-1");
        vars.put("mdoc_dataId", "mdoc-1");
        vars.put("work_dir", workRoot.getAbsolutePath());
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        File expectedWorkDir = new File(new File(workRoot, "mdoc"), "tomo_A");
        File expectedOutput = new File(expectedWorkDir, "tomo_A_raw_bin.mrc");
        File expectedTitle = new File(expectedWorkDir, "tomo_A.rawtlt");
        File expectedScript = new File(expectedWorkDir, "tomo_A_mdoc_stack.sh");

        assertThat(vars.get("mdocStackFiles")).isEqualTo(List.of("/data/a.mrc", "/data/b.mrc"));
        assertThat(vars.get("mdocStackOutputFile")).isEqualTo(expectedOutput.getAbsolutePath());
        assertThat(vars.get("mdocStackTitleFile")).isEqualTo(expectedTitle.getAbsolutePath());
        assertThat(vars.get("mdocStackWorkDir")).isEqualTo(expectedWorkDir.getAbsolutePath());
        assertThat(vars.get("mdocStackScript")).isEqualTo(expectedScript.getAbsolutePath());

        String expectedCmd = "mdoc_stack --files /data/a.mrc,/data/b.mrc --output " + expectedOutput.getAbsolutePath();
        assertThat(vars.get("mdocStackCommand")).isEqualTo(expectedCmd);

        String scriptText = Files.readString(expectedScript.toPath(), StandardCharsets.UTF_8);
        assertThat(scriptText)
                .startsWith("#!/usr/bin/env bash")
                .contains("set -euo pipefail")
                .contains(expectedCmd);

        ArgumentCaptor<MdocStackInputs> captor = ArgumentCaptor.forClass(MdocStackInputs.class);
        verify(activity).runStack(captor.capture());
        MdocStackInputs invoked = captor.getValue();
        assertThat(invoked.files()).containsExactly("/data/a.mrc", "/data/b.mrc");
        assertThat(invoked.outputMrc().getAbsolutePath()).isEqualTo(expectedOutput.getAbsolutePath());
        assertThat(invoked.workDir().getAbsolutePath()).isEqualTo(expectedWorkDir.getAbsolutePath());
    }

    private static MdocTiltMeta tilt(String dataId, String motionResultId, double angle) {
        MdocTiltMeta t = new MdocTiltMeta();
        t.setDataId(dataId);
        t.setMotionResultId(motionResultId);
        t.setTiltAngle(angle);
        return t;
    }

    private static MovieResult movieResult(String id, String movieDataId, String dwPath) {
        MovieResult mr = new MovieResult();
        mr.setId(id);
        mr.setMovie_data_id(movieDataId);
        MotionResult motion = new MotionResult();
        motion.setDw(new MrcFile(dwPath));
        mr.setMotion(motion);
        return mr;
    }

    /**
     * 用最小可控的 stub 模拟 Camunda execution：
     * - {@link DelegateExecution#getVariable} / {@link DelegateExecution#getVariableTyped} 走 vars Map
     * - {@link DelegateExecution#setVariable} 直接回写 vars Map（供断言）
     */
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
