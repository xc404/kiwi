package com.cryo.task.movie.handler.vfm;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.common.error.RetryException;
import com.cryo.model.InstanceResult;
import com.cryo.model.Movie;
import com.cryo.model.MovieResult;
import com.cryo.model.export.ExportMovie;
import com.cryo.service.vfm.VFMParams;
import com.cryo.service.vfm.VfmService;
import com.cryo.task.engine.BaseContext;
import com.cryo.task.engine.Context;
import com.cryo.task.engine.HandlerKey;
import com.cryo.model.MovieImage;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.engine.StepResult;
import com.cryo.model.Task;
import com.cryo.model.settings.TaskSettings;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.CmdException;
import com.cryo.service.cmd.SoftwareExe;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.movie.handler.ctf.EstimationResult;
import com.cryo.task.movie.handler.slurm.SlurmSupport;
import com.cryo.task.support.ExportSupport;
import com.cryo.task.utils.TaskUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class VFMSupport
{
    public static final String VFM_LOG_SUFFIX = "_predicted_boxes.txt";
    public static final String _DENOISED_SUFFIX = "_denoised";
//    private final SoftwareService softwareService;
    private final FilePathService filePathService;
//    private final SlurmSupport slurmSupport;
    private final VfmService vfmService;
    private final ExportSupport exportSupport;
    public StepResult handle(BaseContext movieContext) {
        Movie movie = (Movie) movieContext.getInstance();
        boolean isCryosparc = false;
        if(movie instanceof ExportMovie ){
            isCryosparc = ((ExportMovie) movie).getIsCryosparc();
        }
        Task task = movieContext.getTask();
        MovieResult result = (MovieResult) movieContext.getResult();
//        TaskDataset taskDataset = movieContext.getTaskDataset();
        TaskSettings taskSettings = movieContext.getTaskSettings();
        String file = result.getMotion().getSubtarctionOutput();
        String movieNamePerFix = movie.getFile_name() + _DENOISED_SUFFIX;
        File output_dir = this.filePathService.getVFMWorkDir(movieContext);
        File outputFile = new File(output_dir, movieNamePerFix + ".mrc");
        File pngFile = new File(output_dir, movieNamePerFix + ".png");
        File logFile = new File(output_dir, movieNamePerFix + VFM_LOG_SUFFIX);
        if(isCryosparc){
           outputFile = output_dir;
           logFile = new File(output_dir, FileNameUtil.getPrefix(file)+".txt");
        }
        EstimationResult ctfEstimation = result.getCtfEstimation();
        VFMParams vfmParams = new VFMParams();
//        Ctffind5Settings est = taskSettings.getCtf_estimation_settings().getCtffind5();
        vfmParams.setPsize_in(TaskUtils.getP_size(taskSettings, task.getMicroscope()));
        vfmParams.setVol_kv(taskSettings.getAcceleration_kv());
        vfmParams.setCs_mm(taskSettings.getSpherical_aberration());
        vfmParams.setDf1(ctfEstimation.getDefocus_1());
        vfmParams.setDf2(ctfEstimation.getDefocus_2());
        vfmParams.setW(taskSettings.getAmplitude_contrast());
        vfmParams.setPicking(true);
        vfmParams.setDfang(ctfEstimation.getAzimuth_of_astigmatism());
        vfmParams.setPhase_shift(Optional.ofNullable(ctfEstimation.getAdditional_phase_shift()).orElse(0D));
//        SoftwareService.CmdProcess cmdProcess = this.softwareService.vfm(file, outputFile.getAbsolutePath(), vfmParams);
//        TaskStep slurm = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.VFM_SLURM);
//        movie.addCmd(SoftwareExe.vfm.name(), slurm, cmdProcess.toString());
        vfmParams.setCryosparc(isCryosparc);
        StepResult handle = this.vfmService.handle(movieContext, file, outputFile.getAbsolutePath(), vfmParams);
        VFMResult vfmResult = new VFMResult();
        vfmResult.setLogFile(logFile.getAbsolutePath());
        vfmResult.setPngFile(pngFile.getAbsolutePath());

//        vfmResult.setOutputFile(outputFile.getAbsolutePath());
//        TaskStep exportSlurm = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.VFM_SLURM);
//        StepResult handle = this.slurmSupport.handle(movieContext, exportSlurm);

        result.setVfmResult(vfmResult);
        if(handle.isSuccess()){
            try {
                if(!isCryosparc){
                    TaskUtils.checkFileExist(vfmResult.getPngFile());
                }
                TaskUtils.checkFileExist(vfmResult.getLogFile());
            } catch( CmdException e ) {
                log.error(e.getMessage(), e);
                handle.setMessage(e.getMessage());
                handle.setSuccess(false);
                handle.setRetryable(true);
                return handle;
            }
        }
        // copy png;
//        File pngFile = new File(vfmResult.getPngFile());
        if(!isCryosparc) {
            File imgFile = new File(filePathService.getImageWorkDir(movieContext), "vfm_" + pngFile.getName());
            try {
                FileUtils.copyFile(pngFile, imgFile);
                exportSupport.toSelf(imgFile);
                vfmResult.setPngFile(imgFile.getAbsolutePath());
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
            result.addImage(new MovieImage(MovieImage.Type.vfm, imgFile.getAbsolutePath()));
            try {
                List<String> lines = FileUtils.readLines(new File(vfmResult.getLogFile()), StandardCharsets.UTF_8);
                List<VFMPoint> list = lines.stream().map(line -> {
                    String[] words = line.split("\\s+");
                    double u_min = Double.parseDouble(words[0]);
                    double v_min = Double.parseDouble(words[1]);
                    double u_max = Double.parseDouble(words[2]);
                    double v_max = Double.parseDouble(words[3]);
                    double score = Double.parseDouble(words[4]);
                    VFMPoint point = new VFMPoint();
                    point.setScore(score);
                    point.setU_min(u_min);
                    point.setU_max(u_max);
                    point.setV_min(v_min);
                    point.setV_max(v_max);
                    point.setRadius(Math.sqrt(Math.pow(u_max - u_min, 2) + Math.pow(v_max - v_min, 2)) / 2);
                    point.setU_mean((u_min + u_max) / 2);
                    point.setV_mean((v_min + v_max) / 2);
                    return point;
                }).toList();
                vfmResult.setPointList(list);

            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
        }
        return handle;

    }
}
