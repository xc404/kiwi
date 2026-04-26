package com.kiwi.cryoems.bpm.movie.delegate;

import com.kiwi.bpmn.component.slurm.SbatchConfig;
import com.kiwi.bpmn.component.slurm.SlurmJob;
import com.kiwi.bpmn.component.slurm.SlurmTaskManager;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

class MovieDelegatesPipelineTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldProduceBatchCommandOutputsFromPipeline() throws Exception {
        Path movieFile = Files.writeString(tempDir.resolve("movie001.mrc"), "dummy");
        Path motionDir = Files.createDirectories(tempDir.resolve("motion"));
        Path ctfDir = Files.createDirectories(tempDir.resolve("ctf"));
        Path imageDir = Files.createDirectories(tempDir.resolve("images"));

        Map<String, Object> vars = new HashMap<>();
        vars.put("movie", Map.of(
                "file_path", movieFile.toAbsolutePath().toString(),
                "file_name", "movie001"
        ));
        vars.put("task", Map.of(
                "id", "task-1",
                "microscope", "Titan1_k3",
                "taskSettings", Map.of(
                        "binning_factor", 2,
                        "acceleration_kv", 300.0,
                        "spherical_aberration", 2.7,
                        "amplitude_contrast", 0.1,
                        "motion_correction_settings", Map.of(
                                "motionCor2", Map.of("patch", 5)
                        ),
                        "ctf_estimation_settings", Map.of(
                                "ctffind5", Map.of(
                                        "spectrum_size", 512.0,
                                        "min_res", 30.0,
                                        "max_res", 3.0,
                                        "min_defocus", 5000.0,
                                        "max_defocus", 50000.0,
                                        "defocus_step", 500.0
                                )
                        )
                )
        ));
        vars.put("taskDataset", Map.of(
                "id", "dataset-1",
                "gain0", Map.of("usable_path", "/tmp/gain.mrc"),
                "taskDataSetSetting", Map.of(
                        "p_size", 1.12,
                        "total_dose_per_movie", 60.0
                )
        ));
        vars.put("mrcMetadata", Map.of("sections", 40));
        vars.put("motionWorkDir", motionDir.toString());
        vars.put("ctfWorkDir", ctfDir.toString());
        vars.put("imageWorkDir", imageDir.toString());

        DelegateExecution execution = mockExecution(vars);
        MovieDelegateVariableService service = new MovieDelegateVariableService();
        SlurmTaskManager slurmTaskManager = mockSlurmTaskManager();

        new MovieHeaderJavaDelegate(service).execute(execution);
        new MovieMotionCorrectionJavaDelegate(service, slurmTaskManager).execute(execution);
        new MovieCtfEstimationJavaDelegate(service, slurmTaskManager).execute(execution);

        assertEquals(movieFile.toAbsolutePath().toString(), vars.get("movieFilePath"));
        assertTrue(String.valueOf(vars.get("motionCommand")).contains("MotionCor2"));
        assertTrue(String.valueOf(vars.get("motionCommand")).contains("-Gain"));
        assertTrue(String.valueOf(vars.get("motionPngCommand")).contains("mrc_png"));
        assertNotNull(vars.get("motionOutputFile"));
        assertEquals("Sbatch", vars.get("slurmCmd"));
        assertNotNull(vars.get("slurm_job_name"));
        assertNotNull(vars.get("slurm_output_file"));
        assertNotNull(vars.get("slurm_error_file"));
        assertEquals("job-123", vars.get("slurmJobId"));
        assertNotNull(vars.get("sbatchFilePath"));

        assertTrue(String.valueOf(vars.get("ctfCommand")).contains("ctffind5"));
        assertTrue(String.valueOf(vars.get("ctfCommand")).contains("--step"));
        assertEquals(vars.get("motionOutputFile"), vars.get("ctfInputFile"));
        assertTrue(String.valueOf(vars.get("ctfPngCommand")).contains("ctf_png"));
        assertTrue(String.valueOf(vars.get("command")).contains("ctffind5"));
    }

    @Test
    void shouldSkipWhenExistingResultsAndNoForceReset() throws Exception {
        Path movieFile = Files.writeString(tempDir.resolve("movie002.mrc"), "dummy");
        Map<String, Object> vars = new HashMap<>();
        vars.put("movie", Map.of(
                "file_path", movieFile.toAbsolutePath().toString(),
                "file_name", "movie002"
        ));
        vars.put("task", Map.of("id", "task-2", "taskSettings", Map.of()));
        vars.put("taskDataset", Map.of());
        vars.put("motionPredictDose", 2.0);
        vars.put("ctfStigmaX", 1.0);

        DelegateExecution execution = mockExecution(vars);
        MovieDelegateVariableService service = new MovieDelegateVariableService();
        SlurmTaskManager slurmTaskManager = mockSlurmTaskManager();

        new MovieMotionCorrectionJavaDelegate(service, slurmTaskManager).execute(execution);
        new MovieCtfEstimationJavaDelegate(service, slurmTaskManager).execute(execution);

        assertEquals(true, vars.get("motionSkipped"));
        assertEquals("predict_dose_exists", vars.get("motionSkipReason"));
        assertEquals(true, vars.get("ctfSkipped"));
        assertEquals("stigma_x_exists", vars.get("ctfSkipReason"));
        assertFalse(vars.containsKey("motionCommand"));
        assertFalse(vars.containsKey("ctfCommand"));
    }

    @Test
    void shouldSkipAllDelegatesWhenRollbackEnabled() throws Exception {
        Path movieFile = Files.writeString(tempDir.resolve("movie003.mrc"), "dummy");
        Map<String, Object> vars = new HashMap<>();
        vars.put("movie", Map.of(
                "file_path", movieFile.toAbsolutePath().toString(),
                "file_name", "movie003"
        ));
        vars.put("task", Map.of("id", "task-3", "taskSettings", Map.of()));
        vars.put("taskDataset", Map.of());
        vars.put("movieDelegateRollback", true);

        DelegateExecution execution = mockExecution(vars);
        MovieDelegateVariableService service = new MovieDelegateVariableService();
        SlurmTaskManager slurmTaskManager = mockSlurmTaskManager();

        new MovieHeaderJavaDelegate(service).execute(execution);
        new MovieMotionCorrectionJavaDelegate(service, slurmTaskManager).execute(execution);
        new MovieCtfEstimationJavaDelegate(service, slurmTaskManager).execute(execution);

        assertEquals(true, vars.get("headerSkipped"));
        assertEquals("rollback_enabled", vars.get("headerSkipReason"));
        assertEquals(true, vars.get("motionSkipped"));
        assertEquals("rollback_enabled", vars.get("motionSkipReason"));
        assertEquals(true, vars.get("ctfSkipped"));
        assertEquals("rollback_enabled", vars.get("ctfSkipReason"));
        assertNull(vars.get("motionCommand"));
        assertNull(vars.get("ctfCommand"));
    }

    private static DelegateExecution mockExecution(Map<String, Object> vars) {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> vars.get(inv.getArgument(0)));
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Object val = inv.getArgument(1);
            vars.put(key, val);
            return null;
        }).when(execution).setVariable(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        return execution;
    }

    private static SlurmTaskManager mockSlurmTaskManager() {
        SlurmTaskManager manager = mock(SlurmTaskManager.class);
        when(manager.submitSlurmJob(any(DelegateExecution.class), any(SbatchConfig.class)))
                .thenAnswer(inv -> {
                    SbatchConfig cfg = inv.getArgument(1);
                    SlurmJob job = new SlurmJob();
                    job.setJobId("job-123");
                    job.setJobName(cfg.getJobName());
                    job.setSbatchFilePath("/tmp/mock.sbatch");
                    job.setOutputFilePath(cfg.getOutput_file());
                    job.setErrorFilePath(cfg.getError_file());
                    return CompletableFuture.completedFuture(job);
                });
        return manager;
    }
}
