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
import org.mockito.ArgumentMatchers;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CryoemsMdocStackAndFilterActivityTest {

    private MDocRepository mDocRepository;
    private MDocInstanceRepository mDocInstanceRepository;
    private MovieResultRepository movieResultRepository;
    private MdocStackInputBuilder inputBuilder;

    @BeforeEach
    void setUp() {
        mDocRepository = mock(MDocRepository.class);
        mDocInstanceRepository = mock(MDocInstanceRepository.class);
        movieResultRepository = mock(MovieResultRepository.class);
        inputBuilder = new MdocStackInputBuilder(
                mDocRepository, mDocInstanceRepository, movieResultRepository, new MdocPathResolver());
    }

    @Test
    void execute_publishesProcessVariablesAndDoesNotRewriteIdsWhenAllKept(@TempDir File workRoot) throws Exception {
        seedRepositories("mdoc-1", "inst-1", "tomo_A",
                List.of(
                        tilt("data-1", "mid-1", -10.0),
                        tilt("data-2", "mid-2", 10.0)),
                List.of(
                        movieResult("mid-1", "data-1", "/data/a.mrc"),
                        movieResult("mid-2", "data-2", "/data/b.mrc")));

        CryoemsMdocStackAndFilterActivity activity = newActivity(inputs -> {
            File exclude = new File(inputs.workDir(), inputs.instance().getName() + "_raw_bin_files.txt");
            Files.writeString(exclude.toPath(),
                    String.join(",", inputs.files()),
                    StandardCharsets.UTF_8);
        });

        Map<String, Object> vars = baseVars(workRoot, "inst-1", "mdoc-1");
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        File workDir = new File(new File(workRoot, "mdoc"), "tomo_A");
        File output = new File(workDir, "tomo_A_raw_bin.mrc");
        File titleFile = new File(workDir, "tomo_A_raw_bin.rawtilt");
        File excludeFile = new File(workDir, "tomo_A_raw_bin_files.txt");

        assertThat(vars.get("mdocStackFiles")).isEqualTo(List.of("/data/a.mrc", "/data/b.mrc"));
        assertThat(vars.get("mdocStackOutputFile")).isEqualTo(output.getAbsolutePath());
        assertThat(vars.get("mdocStackTitleFile")).isEqualTo(titleFile.getAbsolutePath());
        assertThat(vars.get("mdocStackExcludeFile")).isEqualTo(excludeFile.getAbsolutePath());
        assertThat(vars.get("mdocStackWorkDir")).isEqualTo(workDir.getAbsolutePath());

        verify(mDocRepository, times(0)).save(any(MDoc.class));
    }

    @Test
    void execute_rewritesMovieDataIdsWhenStackAndFilterDropsSomeTilts(@TempDir File workRoot) throws Exception {
        seedRepositories("mdoc-2", "inst-2", "tomo_B",
                List.of(
                        tilt("data-1", "mid-1", -30.0),
                        tilt("data-2", "mid-2", 0.0),
                        tilt("data-3", "mid-3", 30.0)),
                List.of(
                        movieResult("mid-1", "data-1", "/data/a.mrc"),
                        movieResult("mid-2", "data-2", "/data/b.mrc"),
                        movieResult("mid-3", "data-3", "/data/c.mrc")));

        // 第二轮 findAllById 是 Activity 在过滤后回查 MovieResult 用的（与 build 内部用的是同一组 id）。
        when(movieResultRepository.findAllById(List.of("mid-1", "mid-2", "mid-3")))
                .thenReturn(List.of(
                        movieResult("mid-1", "data-1", "/data/a.mrc"),
                        movieResult("mid-2", "data-2", "/data/b.mrc"),
                        movieResult("mid-3", "data-3", "/data/c.mrc")));

        CryoemsMdocStackAndFilterActivity activity = newActivity(inputs -> {
            File exclude = new File(inputs.workDir(), inputs.instance().getName() + "_raw_bin_files.txt");
            // 过滤后保留 a.mrc, c.mrc，丢弃 b.mrc
            Files.writeString(exclude.toPath(), "/data/a.mrc,/data/c.mrc", StandardCharsets.UTF_8);
        });

        Map<String, Object> vars = baseVars(workRoot, "inst-2", "mdoc-2");
        DelegateExecution execution = stubExecution(vars);

        activity.execute(execution);

        assertThat(vars.get("mdocStackFiles")).isEqualTo(List.of("/data/a.mrc", "/data/c.mrc"));
        ArgumentCaptor<MDoc> captor = ArgumentCaptor.forClass(MDoc.class);
        verify(mDocRepository).save(captor.capture());
        assertThat(captor.getValue().getMovie_data_ids()).containsExactly("data-1", "data-3");
    }

    private CryoemsMdocStackAndFilterActivity newActivity(StackAndFilterStub stub) {
        CryoemsMdocStackAndFilterActivity activity =
                spy(new CryoemsMdocStackAndFilterActivity(inputBuilder, mDocRepository));
        ReflectionTestUtils.setField(activity, "stackAndFilterCommand", "stack_and_filter");
        ReflectionTestUtils.setField(activity, "timeoutSeconds", 60L);
        try {
            doAnswer(inv -> {
                MdocStackInputs inputs = inv.getArgument(0);
                stub.run(inputs);
                return null;
            }).when(activity).runStackAndFilter(any(MdocStackInputs.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return activity;
    }

    private void seedRepositories(
            String mdocId,
            String instanceId,
            String name,
            List<MdocTiltMeta> tilts,
            List<MovieResult> movieResults) {
        MDoc mDoc = new MDoc();
        mDoc.setId(mdocId);
        MdocMeta meta = new MdocMeta();
        meta.setTilts(tilts);
        mDoc.setMeta(meta);
        MDocInstance instance = new MDocInstance();
        instance.setId(instanceId);
        instance.setName(name);
        when(mDocRepository.findById(eq(mdocId))).thenReturn(Optional.of(mDoc));
        when(mDocInstanceRepository.findById(eq(instanceId))).thenReturn(Optional.of(instance));
        when(movieResultRepository.findAllById(ArgumentMatchers.<Iterable<String>>any()))
                .thenReturn(movieResults);
    }

    private static Map<String, Object> baseVars(File workRoot, String instanceId, String mdocDataId) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("mdoc_id", instanceId);
        vars.put("mdoc_dataId", mdocDataId);
        vars.put("work_dir", workRoot.getAbsolutePath());
        return vars;
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

    @FunctionalInterface
    private interface StackAndFilterStub {
        void run(MdocStackInputs inputs) throws Exception;
    }
}
