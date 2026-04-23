package com.cryo.task.utils;

import com.cryo.model.Microscope;
import com.cryo.model.settings.MotionCorrectionSettings;
import com.cryo.model.settings.TaskSettings;
import com.cryo.service.cmd.CmdException;
import org.awaitility.core.ConditionTimeoutException;

import java.io.File;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

public class TaskUtils
{

    public static double getP_size(TaskSettings taskSettings, String microscope) {
        MotionCorrectionSettings motionCorrectionSettings = taskSettings.getMotion_correction_settings();
        double binning = taskSettings.getBinning_factor();
        if( microscope == "Titan3_falcon"
                && motionCorrectionSettings.getMotionCor2().getEer_sampling() != null
                && motionCorrectionSettings.getMotionCor2().getEer_sampling() != 0
        ) {

            binning = binning / motionCorrectionSettings.getMotionCor2().getEer_sampling();
        }
        return taskSettings.getTaskDataSetSetting().getP_size() * binning;


    }

    public static void checkFileExist(String path) {
        File file = new File(path);
        try {
            await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(120)).until(() -> file.exists());
            await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(120)).until(() -> file.length() > 0);
        } catch( ConditionTimeoutException e ) {
            throw new CmdException(file + " not exist");
        }
    }

}
