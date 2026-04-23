package com.cryo.task.movie.handler.motion;

import cn.hutool.core.io.FileUtil;
import com.cryo.model.Microscope;
import com.cryo.model.MicroscopeConfig;
import com.cryo.model.Movie;
import com.cryo.model.MrcFile;
import com.cryo.model.MrcMetadata;
import com.cryo.model.Task;
import com.cryo.service.MicroscopeService;
import com.cryo.service.cmd.SoftwareExe;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.movie.MrcFileMetaSupport;
import com.cryo.task.support.ExportSupport;
import com.cryo.task.utils.TaskUtils;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@Data
public class MotionCor2Support
{
    private final MrcFileMetaSupport mrcFileMetaSupport;
    private final MotionNpy motionNpy;
    private final SoftwareService softwareService;
    private final MicroscopeService microscopeService;
    private final ExportSupport exportSupport;
    @Value("${app.motion.predict-dose.enabled:true}")
    private boolean predictDoseEnabled;

    public void handle(MovieContext movieContext) {
        Movie movie = movieContext.getMovie();
        MotionResult motionCorrection = movieContext.getResult().getMotion();
        mrcResult(motionCorrection);
        MotionFile.Meta motionMeta = getMotionMeta(movieContext);
        Integer patch = movieContext.getTaskSettings().getMotion_correction_settings().getMotionCor2().getPatch();
        motionNpy(movie, patch, motionCorrection.getLocal_motion(), "local");
        motionNpy(movie, patch, motionCorrection.getRigid_motion(), "rigid");
        motionCorrection.getLocal_motion().setMeta(motionMeta);
        motionCorrection.getRigid_motion().setMeta(motionMeta);
        if(predictDoseEnabled){

            double predictDose = predictDose(movieContext, new File(motionCorrection.getNo_dw().getPath()));
            motionCorrection.setPredict_dose(predictDose);
        }
    }

    private MotionFile.Meta getMotionMeta(MovieContext movieContext) {
        Task task = movieContext.getTask();
//        MotionCorrectionSettings motionCorrectionSettings = movieContext.getTaskSettings().getMotion_correction_settings();
//        int binning = task.getBinning_factor();
//        if( task.getMicroscope() == Microscope.Titan3_falcon
//                && motionCorrectionSettings.getMotionCor2().getEer_sampling() != null
//                && motionCorrectionSettings.getMotionCor2().getEer_sampling() != 0
//        ) {
//            binning = binning / motionCorrectionSettings.getMotionCor2().getEer_sampling();
//        }
        double dw_new_pixsize = TaskUtils.getP_size(movieContext.getTaskSettings(), task.getMicroscope());
//        double dw_new_pixsize = task.getP_size() * binning;
        MrcMetadata mrcMetadata = movieContext.getResult().getMrcMetadata();

        return new MotionFile.Meta(
                0,
                mrcMetadata.getSections(),
                0,
                dw_new_pixsize
        );
    }

    private void mrcResult(MotionResult motionResult) {

        getMetaData(motionResult.getDw());
        getMetaData(motionResult.getNo_dw());
//        getMetaData(motionResult.getDws());
    }

    private void getMetaData(MrcFile mrcFile) {
        String path = mrcFile.getPath();
        File file = new File(path);
        File headerFile = new File(file.getParent(), FileUtil.getPrefix(file) + "_meta.txt");
        MrcMetadata metaData = mrcFileMetaSupport.getMetaData(path, headerFile);
        mrcFile.setMetadata(metaData);
    }


    private void motionNpy(Movie movie, Integer patch, MotionFile motionFile, String type) {
        String logFile = motionFile.getPatch_log_file();
        String parent = (new File(logFile)).getParent();
        String trajFile = new File(parent, movie.getFile_name() + "_" + type + "_motion_traj.npy").getAbsolutePath();
        if( "local".equals(type) ) {
            motionNpy.writePatchNpy(logFile, trajFile);

        } else {
            motionNpy.writeFullNpy(logFile, trajFile);
        }
        exportSupport.toSelf(new File(trajFile));
        motionFile.setPath(trajFile);
    }

    private double predictDose(MovieContext movieContext, File outputFile) {
        String microscope = movieContext.getTask().getMicroscope();
        MicroscopeConfig microscopeConfig = microscopeService.getMicroscopeConfig(microscope);
        SoftwareExe predictDose = microscopeConfig.getPredict_dose();
        SoftwareService.CmdProcess cmdProcess = this.softwareService.titanMean(predictDose, outputFile.getAbsolutePath());
        cmdProcess.startAndWait();
        String output = cmdProcess.result();
        String[] split = output.split(" +");
        double dose = Double.parseDouble(StringUtils.trim(split[split.length - 1]));
        double pSize = TaskUtils.getP_size(movieContext.getTaskSettings(), microscope);
        return 5.1 * dose / pSize / pSize;
    }


}
