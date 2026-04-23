package com.cryo.task.movie.handler.ctf;

import com.cryo.model.MicroscopeConfig;
import com.cryo.model.Movie;
import com.cryo.model.MovieImage;
import com.cryo.model.Task;
import com.cryo.model.settings.Ctffind5Settings;
import com.cryo.model.settings.TaskSettings;
import com.cryo.service.FilePathService;
import com.cryo.service.MicroscopeService;
import com.cryo.service.cmd.SoftwareExe;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.utils.TaskUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class Ctffind5Support
{

    public static final String CTFFIND5_OUTPUT_SUFFIX = "_freq.mrc";
    public static final String CTFFIND5_LOG_SUFFIX = "_freq.txt";
    public static final String CTFFIND5_AVROT_FILE_SUFFIX = "_freq_avrot.txt";
    private final FilePathService filePathService;
    private final SoftwareService softwareService;
    private final MicroscopeService microscopeService;

    public void estimate(MovieContext movieContext) {
        Movie movie = movieContext.getMovie();
        Task task = movieContext.getTask();
//        TaskDataset taskDataset = movieContext.getTaskDataset();
        TaskSettings taskSettings = movieContext.getTaskSettings();
        String file = movieContext.getResult().getMotion().getNo_dw().getPath();
        String movieNamePerFix = movie.getFile_name();
        File output_dir = this.filePathService.getEstimationWorkDir(movieContext);
        File outputFile = new File(output_dir, movieNamePerFix + CTFFIND5_OUTPUT_SUFFIX);
        File logFile = new File(output_dir, movieNamePerFix + CTFFIND5_LOG_SUFFIX);
        File avrotFile = new File(output_dir, movieNamePerFix + CTFFIND5_AVROT_FILE_SUFFIX);
        SoftwareService.CtffindParams ctffindParams = new SoftwareService.CtffindParams();
        Ctffind5Settings est = taskSettings.getCtf_estimation_settings().getCtffind5();
        ctffindParams.setPixel_size(TaskUtils.getP_size(taskSettings, task.getMicroscope()));
        ctffindParams.setAccel_kv(taskSettings.getAcceleration_kv());
        ctffindParams.setCs_mm(taskSettings.getSpherical_aberration());
        ctffindParams.setAmp_contrast(taskSettings.getAmplitude_contrast());
        ctffindParams.setSpectrum_size(est.getSpectrum_size());
        ctffindParams.setMin_res(est.getMin_res());
        ctffindParams.setMax_res(est.getMax_res());
        ctffindParams.setMin_defocus(est.getMin_defocus());
        ctffindParams.setMax_defocus(est.getMax_defocus());
        ctffindParams.setDefocus_step(est.getDefocus_step());
        SoftwareService.CmdProcess cmdProcess = this.softwareService.ctffind5(file, outputFile.getAbsolutePath(), ctffindParams);
        TaskStep slurm = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.Slurm);
        movie.addCmd(SoftwareExe.ctffind5.name(), slurm, cmdProcess.toString());


        EstimationResult estimationResult = new EstimationResult();
        estimationResult.setLogFile(logFile.getAbsolutePath());
        estimationResult.setAvrotFile(avrotFile.getAbsolutePath());
        estimationResult.setOutputFile(outputFile.getAbsolutePath());
        String imageOutputFile = new File(this.filePathService.getImageWorkDir(movieContext), movie.getFile_name() + "_freq.png").getAbsolutePath();
        movie.addCmd(SoftwareExe.ctffind5.name() + "_png", slurm, this.softwareService.ctf_png(outputFile.getAbsolutePath(), imageOutputFile).toString());
        movieContext.getResult().addImage(new MovieImage(MovieImage.Type.ctf, imageOutputFile));
        movieContext.getResult().setCtfEstimation(estimationResult);
    }

    public void result(MovieContext movieContext) {
        Task task = movieContext.getTask();
        EstimationResult estimationResult = movieContext.getResult().getCtfEstimation();
        parseLogFile(estimationResult);
        calculate_stigma(task, estimationResult);
    }


    private void parseLogFile(EstimationResult estimationResult) {
        try {
            String logFile = estimationResult.getLogFile();
            List<String> lines = FileUtils.readLines(new File(logFile), StandardCharsets.UTF_8);
            String line = lines.get(lines.size() - 1);
            String[] split = line.split(" +");
            estimationResult.setMicrograph_number(Double.parseDouble(split[0]));
            estimationResult.setDefocus_1(Double.parseDouble(split[1]));
            estimationResult.setDefocus_2(Double.parseDouble(split[2]));
            estimationResult.setAzimuth_of_astigmatism(Double.parseDouble(split[3]));
            estimationResult.setAdditional_phase_shift(Double.parseDouble(split[4]));
            estimationResult.setCross_correlation(Double.parseDouble(split[5]));
            estimationResult.setSpacing(parseDouble(split[6]));
            estimationResult.setEstimated_tilt_axis_angle(Double.parseDouble(split[7]));
            estimationResult.setEstimated_tilt_angle(Double.parseDouble(split[8]));
            estimationResult.setEstimated_sample_thickness(Double.parseDouble(split[9]));
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    private static Double parseDouble(String txt) {
        if( !NumberUtils.isCreatable(txt) ) {
            return null;
        }
        return Double.parseDouble(txt);
    }

    private void calculate_stigma(Task task, EstimationResult estimationResult) {
        MicroscopeConfig microscopeConfig = this.microscopeService.getMicroscopeConfig(task.getMicroscope());
        MicroscopeConfig.MicroscopeTEMstigma microscopeTemstigma = microscopeConfig.getMicroscope_temstigma();
        var x_TEMstigma_angle = microscopeTemstigma.getX_temstigma_angle();
        var y_TEMstigma_angle = microscopeTemstigma.getY_temstigma_angle();
        var x_TEMstigma_step = microscopeTemstigma.getX_temstigma_step();
        var y_TEMstigma_step = microscopeTemstigma.getY_temstigma_step();
        var stigma_angle = estimationResult.getAzimuth_of_astigmatism();
        var x_angle_diff = x_TEMstigma_angle - stigma_angle;
        var defocus_u = estimationResult.getDefocus_1();
        var defocus_v = estimationResult.getDefocus_2();
        var kx1 = Math.sin(Math.toRadians(x_angle_diff)) / Math.cos(Math.toRadians(x_angle_diff));
        x_angle_diff += 90;
        var kx2 = Math.sin(Math.toRadians(x_angle_diff)) / Math.cos(Math.toRadians(x_angle_diff));

        var Lx_sub1 = Math.sqrt((kx1 * kx1 + 1) / (defocus_v * defocus_v + defocus_u * defocus_u * kx1 * kx1));
        var Lx_sub2 = Math.sqrt((kx2 * kx2 + 1) / (defocus_v * defocus_v + defocus_u * defocus_u * kx2 * kx2));
        var Lx = 0.0001 * defocus_u * defocus_v * (Lx_sub1 - Lx_sub2) / x_TEMstigma_step;

        // Calculations for Ly
        var y_angle_diff = y_TEMstigma_angle - stigma_angle;
        var ky1 = Math.sin(Math.toRadians(y_angle_diff)) / Math.cos(Math.toRadians(y_angle_diff));
        y_angle_diff += 90;
        var ky2 = Math.sin(Math.toRadians(y_angle_diff)) / Math.cos(Math.toRadians(y_angle_diff));

        var Ly_sub1 = Math.sqrt((ky1 * ky1 + 1) / (defocus_v * defocus_v + defocus_u * defocus_u * ky1 * ky1));
        var Ly_sub2 = Math.sqrt((ky2 * ky2 + 1) / (defocus_v * defocus_v + defocus_u * defocus_u * ky2 * ky2));
        var Ly = 0.0001 * defocus_u * defocus_v * (Ly_sub1 - Ly_sub2) / y_TEMstigma_step;

        // Calculate new stigma values
        var new_stigma_x = -Lx;
        var new_stigma_y = -Ly;


        //todo change x, y name
        estimationResult.setStigma_x(new_stigma_y);
        estimationResult.setStigma_y(new_stigma_x);

    }

//    private double getP_size(Task task) {
//        MotionCorrectionSettings motionCorrectionSettings = task.getMotion_correction_settings();
//        double binning = task.getBinning_factor();
//        if( task.getMicroscope() == Microscope.Titan3_falcon
//                && motionCorrectionSettings.getMotionCor2().getEer_sampling() != null
//                && motionCorrectionSettings.getMotionCor2().getEer_sampling() != 0
//        ) {
//            binning = binning / motionCorrectionSettings.getMotionCor2().getEer_sampling();
//        }
//        return task.getP_size() * binning;
//
//
//    }

}
