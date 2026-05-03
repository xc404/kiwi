package com.cryo.task.movie.handler.motion;

import cn.hutool.core.io.FileUtil;
import com.cryo.model.Microscope;
import com.cryo.model.MicroscopeConfig;
import com.cryo.model.Movie;
import com.cryo.task.engine.HandlerKey;
import com.cryo.model.MovieImage;
import com.cryo.model.MovieResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.model.MrcFile;
import com.cryo.task.engine.StepResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.model.settings.MotionCor2Settings;
import com.cryo.model.settings.TaskSettings;
import com.cryo.service.FilePathService;
import com.cryo.service.MicroscopeService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.engine.Handler;
import com.cryo.task.support.ExportSupport;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MotionCor2 implements Handler<MovieContext>
{

    public static final String PATCH_PATCH_LOG_SUFFIX = "0-Patch-Patch.log";
    private final SoftwareService softwareService;
    private final FilePathService filePathService;
    private final MicroscopeService microscopeService;
    private final ExportSupport exportSupport;
    @Getter
    @Value("${app.motion.defect.path:/home/cryoems/bin/eer_defect/defect.txt}")
    private String defectFilePath = "/home/cryoems/bin/eer_defect/defect.txt";
    @Getter
    @Value("${app.motion.version:1.4.5}")
    private String motionVersion = "1.4.5";

    @Getter
    @Value("${app.motion.defect.ruijinEnabled:false}")
    private boolean ruijinEnabled = true;


    private void doMotion(MovieContext movieContext) {
        Movie movie = movieContext.getMovie();
        Task task = movieContext.getTask();
        TaskDataset taskDataset = movieContext.getTaskDataset();
        TaskDataset.Gain gain = taskDataset.getGain0();
        TaskSettings taskSettings = movieContext.getTaskSettings();
//        TaskDataset.Gain gain = movieContext.getGain();
        SoftwareService.MotionCor2Params params = new SoftwareService.MotionCor2Params();
        String prefix = movie.getFile_name();
        File output_dir = filePathService.getMotionWorkDir(movieContext);
        File outputFile = new File(output_dir, prefix + ".mrc");
        params.setInput(movie.getFile_path());
        params.setOutput(outputFile.getAbsolutePath());
        MotionCor2Settings motionCorrection = taskSettings.getMotion_correction_settings().getMotionCor2();
        params.setGain(gain.getUsable_path());
        String microscope = task.getMicroscope();
        String logFile = output_dir + "/" + prefix + "_log";
        params.setBinning(taskSettings.getBinning_factor());
        params.setPatch(motionCorrection.getPatch());
        params.setPixsize(taskDataset.getTaskDataSetSetting().getP_size());
        params.setAccel_kv(taskSettings.getAcceleration_kv());
        params.setMicroscope(microscope);
        Integer nz = movieContext.getResult().getMrcMetadata().getSections();
        double fmdose = taskDataset.getTaskDataSetSetting().getTotal_dose_per_movie() / nz;
        switch( microscope ) {
            case "Titan1_k3":
            case "Titan2_k3":
                MicroscopeConfig microscopeConfig = this.microscopeService.getMicroscopeConfig(microscope);
                MicroscopeConfig.Scale closetScale = microscopeConfig.getClosetScale(taskDataset.getTaskDataSetSetting().getP_size());
                if( closetScale != null ) {
                    params.setMinor_scale(closetScale.getMinor_scale());
                    params.setMajor_scale(closetScale.getMajor_scale());
                    params.setDistort_ang(closetScale.getDistort_ang());
                }
                params.setFmdose(fmdose);
                break;
            case "Titan3_falcon":
                params.setEer_sampling(motionCorrection.getEer_sampling());
                Integer eer_fraction = motionCorrection.getEer_fraction();
                if(params.getEer_sampling() == 2){
                    params.setDefectFile(defectFilePath);
                }
                int err = nz/eer_fraction;
                File eer_frac_path = new File(output_dir, prefix + ".fraction");
                String line = nz + "\t" + err + "\t" + fmdose;
                FileUtil.writeLines(List.of(line), eer_frac_path, StandardCharsets.UTF_8);
                exportSupport.toSelf(eer_frac_path);
                params.setEer_frac_path(eer_frac_path.getAbsolutePath());
                break;
            default:
                throw new RuntimeException(String.format("Microscope %s is not supported.", microscope));
        }
        SoftwareService.CmdProcess cmdProcess = softwareService.motionCor2(movie.getFile_name(), params, logFile);
        TaskStep slurm = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.Slurm);
        movie.addCmd(this.support().name(), slurm, cmdProcess.toCmdStr());
        MotionResult motionResult = getMotionCorrectionOutput(movie, outputFile, logFile);
        String imageOutputFile = new File(this.filePathService.getImageWorkDir(movieContext), movie.getFile_name() + "_DW_thumb_@1024.png").getAbsolutePath();
//        if(task.getMicroscope() == Microscope.Titan3_falcon){
//            movie.addCmd(this.support().name() + "_png", slurm, softwareService.titan3_mrc_png(motionResult.getDw().getPath(), imageOutputFile).toCmdStr());
//        }else{
        movie.addCmd(this.support().name() + "_png", slurm, softwareService.mrc_png(motionResult.getDw().getPath(), imageOutputFile).toCmdStr());
//        }
        String output = new File(output_dir, movie.getFile_name() + "_subtarction.mrc").getAbsolutePath();
        movie.addCmd(this.support().name() + "_subtarction", slurm, softwareService.subtarction(motionResult.getNo_dw().getPath(), output).toCmdStr());
        if(this.ruijinEnabled){
           String input = output;
           output = new File(output_dir, movie.getFile_name() + "_defect_ruijin.mrc").getAbsolutePath();
            movie.addCmd(this.support().name() + "_defect_ruijin", slurm, softwareService.ruijingDefect(input, output).toCmdStr());
        }

        motionResult.setSubtarctionOutput(output);
        movieContext.getResult().addImage(new MovieImage(MovieImage.Type.motion_mrc, imageOutputFile));
        movieContext.getResult().setMotion(motionResult);

    }

    private MotionResult getMotionCorrectionOutput(Movie movie, File outputFile, String logFile) {
        File dw = new File(outputFile.getParent(), movie.getFile_name() + "_DW.mrc");
        File dws = new File(outputFile.getParent(), movie.getFile_name() + "_DWS.mrc");

        MotionResult motionResult = new MotionResult();
        motionResult.setDws(new MrcFile(dws.getAbsolutePath(), null));
        motionResult.setDw(new MrcFile(dw.getAbsolutePath(), null));
        motionResult.setNo_dw(new MrcFile(outputFile.getAbsolutePath(), null));
        if(this.motionVersion.startsWith("1.6")){
            motionResult.setLocal_motion(new MotionFile(new File(outputFile.getParent(), movie.getFile_name() + "-Patch-Patch.log").getAbsolutePath()));
            motionResult.setRigid_motion(new MotionFile(new File(outputFile.getParent(), movie.getFile_name() + "-Patch-Full.log").getAbsolutePath()));
        }else{
            motionResult.setLocal_motion(new MotionFile(logFile + "0-Patch-Patch.log"));
            motionResult.setRigid_motion(new MotionFile(logFile + "0-Patch-Full.log"));
        }

        return motionResult;
    }


    @Override
    public HandlerKey support() {
        return HandlerKey.MOTION_CORRECTION;
    }


    @Override
    public StepResult handle(MovieContext movieContext) {
        MovieResult result = movieContext.getResult();
        if( Optional.ofNullable(result.getMotion()).map(MotionResult::getPredict_dose).isEmpty() || movieContext.forceReset() ) {
            doMotion(movieContext);
            return StepResult.success("Motion Correction Success");
        }
        return StepResult.success("Motion Correction skipped");


    }
}
