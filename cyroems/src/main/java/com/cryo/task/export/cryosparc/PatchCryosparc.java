package com.cryo.task.export.cryosparc;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.CryosparcPatchRepository;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.model.CryosparcPatch;
import com.cryo.model.Instance;
import com.cryo.model.Microscope;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportTask;
import com.cryo.model.settings.CryosparcSettings;
import com.cryo.model.settings.TaskDataSetSetting;
import com.cryo.model.settings.TaskSettings;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.service.cryosparc.CryosparcClient;
import com.cryo.service.cryosparc.CryosparcJob;
import com.cryo.service.cryosparc.JobRequest;
import com.cryo.service.cryosparc.JobState;
import com.cryo.service.cryosparc.JobType;
import com.cryo.task.engine.flow.FlowManager;
import com.cryo.task.export.ExportTaskVo;
import com.cryo.task.export.MovieExportContext;
import com.cryo.task.movie.handler.ctf.EstimationResult;
import com.cryo.task.movie.handler.motion.MotionResult;
import com.cryo.task.support.ExportSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.result.R;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatchCryosparc implements ApplicationContextAware
{

    public static final String IMPORT_MOVIES = "import_movies";
    public static final String PATCH_CTF_ESTIMATION_MULTI = "patch_ctf_estimation_multi";
    public static final String PATCH_MOTION_CORRECTION_MULTI = "patch_motion_correction_multi";
    private final TaskDataSetRepository taskDataSetRepository;
    private final SoftwareService softwareService;
    private final FilePathService filePathService;
    private final ExportSupport exportSupport;
    private ApplicationContext applicationContext;
    private final FlowManager flowManager;
    private final MovieResultRepository movieResultRepository;
    private final ExportMovieRepository exportMovieRepository;
    private final CryosparcPatchRepository cryosparcPatchRepository;

    private final CryosparcMovieService cryosparcMovieService;
    private final CryosparcClient cryosparcClient;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public void handle(ExportTaskVo exportTaskVo) {
        Task task = exportTaskVo.getTask();
        ExportTask exportTask = exportTaskVo.getExportTask();
        CryosparcProject project = exportTask.getCryosparcProject();
        if( project == null ) {
            log.warn("cryosparc proejct not created");
            return;
        }
        TaskDataset dataset = this.taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
//        String output;
//        File inputParamsFile;
        CryosparcPatch cryosparcPatch;

        CryosparcMovieService.PatchMovies patchMovies;
        ReentrantLock reentrantLock = this.locks.computeIfAbsent(exportTaskVo.getExportTask().getTask_id(), k -> new ReentrantLock());
        try{
            reentrantLock.lock();
            log.info("start patch cryosparc");
            patchMovies = this.cryosparcMovieService.getPatchMovies(exportTaskVo);

            if( patchMovies == null || patchMovies.getMovies().isEmpty() ) {
                log.info("no movie ready");
                return;
            }
            cryosparcPatch = new CryosparcPatch();
            cryosparcPatch.setPattern(patchMovies.getPattern());
            cryosparcPatch.setMovies(patchMovies.getMovies().stream().map(Instance::getFile_path).toList());
            cryosparcPatch.setTask_id(task.getId());
            cryosparcPatch.setExportTaskId(exportTask.getId());

            processingMovies(cryosparcPatch, patchMovies);

        }finally {
            reentrantLock.unlock();
        }
        try {
//            SoftwareService.CmdProcess cmdProcess = this.softwareService.cryosparc_motion_job(project, inputParamsFile.getAbsolutePath(), output);
//
//            cmdProcess.startAndWait();
            this.processPipeline(exportTaskVo, cryosparcPatch);

//            JsonNode jsonNode = cryosparcPatch.getCryosparcJobMap().get(PATCH_CTF_ESTIMATION_MULTI).getJobState().getResults();
//            if( !"success".equalsIgnoreCase(jsonNode.path("status").asText()) ) {
//                cryosparcPatch.setError(jsonNode.path("error").asText());
//                this.cryosparcPatchRepository.save(cryosparcPatch);
//                throw new RuntimeException(jsonNode.path("error").asText());
//            }

            CryosparcResult cryosparcResult = new CryosparcResult();
            cryosparcResult.setImport_job_uid(cryosparcPatch.getCryosparcJobMap().get(JobType.import_movies).getJobState().getCs_job_uid());
            cryosparcResult.setMotioncor_job_uid(cryosparcPatch.getCryosparcJobMap().get(JobType.patch_motion_correction_multi).getJobState().getCs_job_uid());
            cryosparcResult.setCtf_job_uid(cryosparcPatch.getCryosparcJobMap().get(JobType.patch_ctf_estimation_multi).getJobState().getCs_job_uid());

            cryosparcPatch.setCryosparcResult(cryosparcResult);

//            Map<String, ExportMovie> movieMap = patchMovies.getMovies().stream().collect(Collectors.toMap(m -> m.getFile_name(), m -> m));
            List<JsonNode> results = cryosparcPatch.getCryosparcJobMap().get(JobType.patch_ctf_estimation_multi).getJobState().getResults();
            for( JsonNode result : results ) {

                String input = FileNameUtil.getPrefix(result.path("import").path("movie_blob/path").asText());
                ExportMovie exportMovie = patchMovies.getMovies().stream().filter(m -> {
                    return input.endsWith(m.getFile_name());
                }).findFirst().orElse(null);
                if( exportMovie == null ) {
                    log.error("movie not exist");
                    continue;
                }
                MovieExportContext movieContext = new MovieExportContext(applicationContext, dataset, flowManager.getMovieExportFlow(task, exportTask), task, exportTask, exportMovie);
                MovieResult movieResult = movieContext.getResult();
                MotionResult motionResult = new MotionResult();
                String dwpath = result.path("motioncor").path("micrograph_blob_non_dw/path").asText();
                motionResult.setSubtarctionOutput(dwpath);
                movieResult.setMotion(motionResult);
                EstimationResult estimationResult = new EstimationResult();
                JsonNode ctfNode = result.path("ctf");
                estimationResult.setDefocus_1(ctfNode.path("ctf/df1_A").asDouble());
                estimationResult.setDefocus_2(ctfNode.path("ctf/df2_A").asDouble());
                estimationResult.setAzimuth_of_astigmatism(ctfNode.path("ctf/df_angle_rad").asDouble() * 180 / Math.PI);
                estimationResult.setAdditional_phase_shift(ctfNode.path("ctf/phase_shift_rad").asDouble());
                movieResult.setCtfEstimation(estimationResult);

                movieResult.setCryosparcResult(cryosparcResult);
                this.movieResultRepository.save(movieResult);
                exportMovie.setWaiting(false);
            }

        } catch( Exception e ) {
            log.error("patch cryosparc error", e);
            this.complete(cryosparcPatch, patchMovies, false, e.getMessage());
            return;
        }
        this.complete(cryosparcPatch, patchMovies, true, null);

    }

    private void processPipeline(ExportTaskVo exportTaskVo, CryosparcPatch cryosparcPatch) {
        TaskDataset dataset = this.taskDataSetRepository.findById(exportTaskVo.getTask().getTaskSettings().getDataset_id()).orElseThrow();
        JobState jobState = this.importJob(exportTaskVo.getTask(), exportTaskVo.getExportTask(), cryosparcPatch, exportTaskVo.getTask().getTaskSettings(), dataset);

        jobState = this.motion(exportTaskVo.getExportTask(), cryosparcPatch, exportTaskVo.getTask().getTaskSettings(), jobState.getCs_job_uid());
        jobState = this.ctf(exportTaskVo.getExportTask(), cryosparcPatch, exportTaskVo.getTask().getTaskSettings(), jobState.getCs_job_uid());

    }


    private void processingMovies(CryosparcPatch cryosparcPatch, CryosparcMovieService.PatchMovies patchMovies) {
        this.cryosparcPatchRepository.save(cryosparcPatch);
        patchMovies.getMovies().forEach(m -> {
            m.setProcess_status(new Instance.ProcessStatus(true, new Date()));
            m.setCryospacStatus(ExportMovie.CryospacStatus.Processing);
            m.setCryosparcPatchId(cryosparcPatch.getId());
            this.exportMovieRepository.save(m);
        });
    }

    private void complete(CryosparcPatch cryosparcPatch, CryosparcMovieService.PatchMovies patchMovies, boolean success, String message) {
        if( success ) {
            cryosparcPatch.setStatus("success");
        } else {
            cryosparcPatch.setStatus("fail");
        }
        cryosparcPatch.setError(message);
        this.cryosparcPatchRepository.save(cryosparcPatch);
        patchMovies.getMovies().forEach(m -> {
            m.setProcess_status(new Instance.ProcessStatus(false, new Date()));
            if( !success ) {
                Instance.ErrorStatus errorStatus = new Instance.ErrorStatus();
                errorStatus.setError_count(1);
                errorStatus.setPermanent(true);
                m.setError(errorStatus);
                m.setStatus(R.status(false, message));
                m.setCryospacStatus(ExportMovie.CryospacStatus.Init);
            } else {
                m.setCryospacStatus(ExportMovie.CryospacStatus.Complete);
            }
            m.getProcess_status().setProcessing(false);
            this.exportMovieRepository.save(m);
        });
    }


    public JobState importJob(Task task, ExportTask exportTask, CryosparcPatch cryosparcPatch, TaskSettings taskSettings, TaskDataset taskDataset) {
        TaskDataSetSetting taskDataSetSetting = taskDataset.getTaskDataSetSetting();
        CryosparcSettings cryosparcSettings = exportTask.getCryosparcSettings();
        String gainref_path = task.getMicroscope() == "Titan3_falcon" ? taskDataset.getGain0().getPath() : taskDataset.getGain0().getUsable_path();
        Map<String, Object> params = Map.of(
                "blob_paths", cryosparcPatch.getPattern(),
                "psize_A", taskDataSetSetting.getP_size(),
                "accel_kv", taskSettings.getAcceleration_kv(),
                "cs_mm", taskSettings.getSpherical_aberration(),
                "total_dose_e_per_A2", taskDataSetSetting.getTotal_dose_per_movie(),
                "gainref_path", gainref_path,
                "gainref_flip_x", cryosparcSettings.isGainref_flip_x(),
                "gainref_flip_y", cryosparcSettings.isGainref_flip_y(),
                "gainref_rotate_num", cryosparcSettings.getGainref_rotate_num(),
                "phase_plate_data", cryosparcSettings.isPhase_plate_data()
        );
        CryosparcProject cryosparcProject = exportTask.getCryosparcProject();
        JobRequest request = new JobRequest(cryosparcProject.getProject_uid(),
                cryosparcProject.getWorkspace_uid(),
                JobType.import_movies,
                params,
                null
        );
        return getCryosparcJob(cryosparcPatch, request);

    }

    private JobState getCryosparcJob(CryosparcPatch cryosparcPatch, JobRequest request) {

        CryosparcJob cryosparcJob = this.cryosparcClient.submitAndWait(request);
        cryosparcPatch.addCryojob(request.getJob_type(), cryosparcJob);
        this.cryosparcPatchRepository.save(cryosparcPatch);
        if( !cryosparcJob.getJobState().isSuccess() ) {
            throw new RuntimeException(cryosparcJob.getJobState().getError());
        }
        return cryosparcJob.getJobState();
    }


    public JobState motion(ExportTask exportTask, CryosparcPatch cryosparcPatch, TaskSettings taskSettings, String ancestor_job_uid) {
        CryosparcSettings cryosparcSettings = exportTask.getCryosparcSettings();
        Map<String, Object> params = Map.of(
                "output_fcrop_factor", "1/" + taskSettings.getBinning_factor(),
                "bfactor", cryosparcSettings.getBfactor(),
                "frame_start", cryosparcSettings.getFrame_start(),
                "output_f16", cryosparcSettings.isOutput_f16(),
                "res_max_align", cryosparcSettings.getMotion_res_max_align(),
                "smooth_lambda_cal", cryosparcSettings.getSmooth_lambda_cal(),
                "variable_dose", cryosparcSettings.isVariable_dose()

        );
        CryosparcProject cryosparcProject = exportTask.getCryosparcProject();
        JobRequest request = new JobRequest(cryosparcProject.getProject_uid(),
                cryosparcProject.getWorkspace_uid(),
                JobType.patch_motion_correction_multi,
                params,
                ancestor_job_uid
        );
        return getCryosparcJob(cryosparcPatch, request);

    }

    public JobState ctf(ExportTask exportTask, CryosparcPatch cryosparcPatch, TaskSettings taskSettings, String ancestor_job_uid) {
        CryosparcSettings cryosparcSettings = exportTask.getCryosparcSettings();
        Map<String, Object> params = Map.of(
                "compute_num_gpus", 4,
                "phase_shift_max", cryosparcSettings.getPhase_shift_max(),
                "phase_shift_min", cryosparcSettings.getPhase_shift_min(),
                "res_max_align", cryosparcSettings.getCtf_res_max_align(),
                "res_min_align", cryosparcSettings.getCtf_res_min_align()
        );
        CryosparcProject cryosparcProject = exportTask.getCryosparcProject();
        JobRequest request = new JobRequest(cryosparcProject.getProject_uid(),
                cryosparcProject.getWorkspace_uid(),
                JobType.patch_ctf_estimation_multi,
                params,
                ancestor_job_uid
        );
        return getCryosparcJob(cryosparcPatch, request);

    }

    private ObjectNode createInputParams(ExportTaskVo exportTaskVo, String patten) {
        Task task = exportTaskVo.getTask();
        ExportTask exportTask = exportTaskVo.getExportTask();

        TaskDataset dataset = taskDataSetRepository.findById(task.getTaskSettings().getDataset_id()).orElseThrow();
//        Task task = context.getTask();
        TaskSettings taskSettings = task.getTaskSettings();
        CryosparcSettings cryosparcSettings = exportTask.getCryosparcSettings();
//        Movie movie = context.getMovie();
        ObjectNode objectNode = JsonUtil.createObjectNode();
        ObjectNode import_config = objectNode.putObject("import_config");
        ObjectNode motioncor_config = objectNode.putObject("motioncor_config");
        ObjectNode ctf_config = objectNode.putObject("ctf_config");

        import_config.put("accel_kv", taskSettings.getAcceleration_kv());
        import_config.put("blob_paths", patten);
        import_config.put("cs_mm", taskSettings.getSpherical_aberration());
        import_config.put("eer_num_fractions", taskSettings.getMotion_correction_settings().getMotionCor2().getEer_fraction());
        import_config.put("eer_upsamp_factor", taskSettings.getMotion_correction_settings().getMotionCor2().getEer_sampling());
        import_config.put("gainref_flip_x", cryosparcSettings.isGainref_flip_x());
        import_config.put("gainref_flip_y", cryosparcSettings.isGainref_flip_y());
        import_config.put("gainref_path", dataset.getGain0().getUsable_path());
        import_config.put("gainref_rotate_num", cryosparcSettings.getGainref_rotate_num());
        import_config.put("phase_plate_data", cryosparcSettings.isPhase_plate_data());
        import_config.put("psize_A", dataset.getTaskDataSetSetting().getP_size());
        import_config.put("total_dose_e_per_A2", dataset.getTaskDataSetSetting().getTotal_dose_per_movie());


        motioncor_config.put("bfactor", cryosparcSettings.getBfactor());
        if( cryosparcSettings.getFrame_end() != null && cryosparcSettings.getFrame_end() > 0 ) {
            motioncor_config.put("frame_end", cryosparcSettings.getFrame_end());
        }
        motioncor_config.put("frame_start", cryosparcSettings.getFrame_start());
        motioncor_config.put("output_f16", cryosparcSettings.isOutput_f16());
        motioncor_config.put("output_fcrop_factor", "1/" + taskSettings.getBinning_factor());
        motioncor_config.put("res_max_align", cryosparcSettings.getMotion_res_max_align());
        motioncor_config.put("smooth_lambda_cal", cryosparcSettings.getSmooth_lambda_cal());
        motioncor_config.put("variable_dose", cryosparcSettings.isVariable_dose());


        ctf_config.put("amp_contrast", taskSettings.getAmplitude_contrast());
        ctf_config.put("df_search_max", taskSettings.getCtf_estimation_settings().getCtffind5().getMax_defocus());
        ctf_config.put("df_search_min", taskSettings.getCtf_estimation_settings().getCtffind5().getMin_defocus());
        ctf_config.put("phase_shift_max", cryosparcSettings.getPhase_shift_max());
        ctf_config.put("phase_shift_min", cryosparcSettings.getPhase_shift_min());
        ctf_config.put("res_max_align", cryosparcSettings.getCtf_res_max_align());
        ctf_config.put("res_min_align", cryosparcSettings.getCtf_res_min_align());

        return objectNode;
    }

    private JsonNode parseOutput(String output) {
        JsonNode jsonNode;
        try {
            jsonNode = JsonUtil.readTree(FileUtils.readFileToString(new File(output), StandardCharsets.UTF_8));
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        return jsonNode;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
