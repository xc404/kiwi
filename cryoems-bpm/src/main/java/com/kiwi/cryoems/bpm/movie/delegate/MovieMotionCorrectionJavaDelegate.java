package com.kiwi.cryoems.bpm.movie.delegate;

import com.kiwi.bpmn.component.slurm.SbatchConfig;
import com.kiwi.bpmn.component.slurm.SlurmJob;
import com.kiwi.bpmn.component.slurm.SlurmTaskManager;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import com.kiwi.bpmn.external.AbstractExternalTaskHandler;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.Map;

@ComponentDescription(
        name = "Movie Motion Correction",
        group = "CryoEMS",
        version = "1.0",
        description = "生成 MotionCor2 批处理命令及其输出文件与日志路径",
        outputs = {
                @ComponentParameter(
                        key = "motionCommand",
                        name = "motionCommand",
                        type = "string",
                        description = "MotionCor2 主命令"),
                @ComponentParameter(
                        key = "motionPngCommand",
                        name = "motionPngCommand",
                        type = "string",
                        description = "motion 预览图生成命令"),
                @ComponentParameter(
                        key = "motionSubtractionCommand",
                        name = "motionSubtractionCommand",
                        type = "string",
                        description = "motion subtraction 命令"),
                @ComponentParameter(
                        key = "motionOutputFile",
                        name = "motionOutputFile",
                        type = "string",
                        description = "MotionCor2 主输出 mrc"),
                @ComponentParameter(
                        key = "motionDwFile",
                        name = "motionDwFile",
                        type = "string",
                        description = "MotionCor2 DW 输出"),
                @ComponentParameter(
                        key = "motionDwsFile",
                        name = "motionDwsFile",
                        type = "string",
                        description = "MotionCor2 DWS 输出"),
                @ComponentParameter(
                        key = "motionLogFile",
                        name = "motionLogFile",
                        type = "string",
                        description = "MotionCor2 日志前缀"),
                @ComponentParameter(
                        key = "motionLocalLogFile",
                        name = "motionLocalLogFile",
                        type = "string",
                        description = "MotionCor2 local motion log"),
                @ComponentParameter(
                        key = "motionRigidLogFile",
                        name = "motionRigidLogFile",
                        type = "string",
                        description = "MotionCor2 rigid motion log"),
                @ComponentParameter(
                        key = "motionSkipped",
                        name = "motionSkipped",
                        type = "boolean",
                        description = "是否跳过该批处理命令"),
                @ComponentParameter(
                        key = "command",
                        name = "command",
                        type = "string",
                        description = "提交给 Slurm 的实际命令"),
                @ComponentParameter(
                        key = "slurmCmd",
                        name = "slurmCmd",
                        type = "string",
                        description = "Slurm 执行方式，固定 Sbatch"),
                @ComponentParameter(
                        key = "slurm_job_name",
                        name = "slurm_job_name",
                        type = "string",
                        description = "Slurm 作业名"),
                @ComponentParameter(
                        key = "slurm_output_file",
                        name = "slurm_output_file",
                        type = "string",
                        description = "Slurm 标准输出日志路径"),
                @ComponentParameter(
                        key = "slurm_error_file",
                        name = "slurm_error_file",
                        type = "string",
                        description = "Slurm 错误日志路径")
        }
)
@Component("movieMotionCorrectionJavaDelegate")
@ExternalTaskSubscription(topicName = "movie-motion-correction", lockDuration = 300000)
public class MovieMotionCorrectionJavaDelegate extends AbstractExternalTaskHandler
{
    private final MovieDelegateVariableService variableService;
    private final SlurmTaskManager slurmTaskManager;

    public MovieMotionCorrectionJavaDelegate(
            MovieDelegateVariableService variableService,
            SlurmTaskManager slurmTaskManager
    ) {
        this.variableService = variableService;
        this.slurmTaskManager = slurmTaskManager;
    }

    @Override
    public CompletableFuture<Void> executeAsync(
            DelegateExecution execution
    ) throws Exception {
        try {
            Object movie = variableService.requiredVariable(execution, "movie");
            Object task = variableService.requiredVariable(execution, "task");
            Object taskDataset = variableService.optionalVariable(execution, "taskDataset");

            Map<String, Object> movieMap = MovieDelegateValueHelper.asMap(movie, "movie");
            Map<String, Object> taskMap = MovieDelegateValueHelper.asMap(task, "task");
            Map<String, Object> taskDatasetMap = taskDataset instanceof Map<?, ?>
                    ? MovieDelegateValueHelper.asMap(taskDataset, "taskDataset")
                    : Map.of();
            Map<String, Object> taskSettingsMap = MovieDelegateValueHelper.nestedMap(taskMap, "taskSettings");

            String movieFilePath = MovieDelegateValueHelper.text(movieMap, "file_path");
            if( !StringUtils.hasText(movieFilePath) ) {
                throw new MovieFatalException("movie.file_path 不能为空");
            }
            String movieFileName = resolveMovieFileName(movieMap, movieFilePath);
            String microscope = MovieDelegateValueHelper.text(taskMap, "microscope");
            String motionDir = resolveWorkDir(execution, "motionWorkDir", "motion");
            String imageDir = resolveWorkDir(execution, "imageWorkDir", "thumbnails");

            Map<String, Object> data = new LinkedHashMap<>();

            String outputMrc = new File(motionDir, movieFileName + ".mrc").getAbsolutePath();
            String dwFile = new File(motionDir, movieFileName + "_DW.mrc").getAbsolutePath();
            String dwsFile = new File(motionDir, movieFileName + "_DWS.mrc").getAbsolutePath();
            String logPrefix = new File(motionDir, movieFileName + "_log").getAbsolutePath();
            String localLog = logPrefix + "0-Patch-Patch.log";
            String rigidLog = logPrefix + "0-Patch-Full.log";
            String previewPng = new File(imageDir, movieFileName + "_DW_thumb_@1024.png").getAbsolutePath();
            String subtractionOutput = new File(motionDir, movieFileName + "_subtarction.mrc").getAbsolutePath();

            Integer binning = MovieDelegateValueHelper.intPath(taskSettingsMap, "binning_factor");
            Integer patch = MovieDelegateValueHelper.intPath(
                    taskSettingsMap,
                    "motion_correction_settings",
                    "motionCor2",
                    "patch"
            );
            Integer eerSampling = MovieDelegateValueHelper.intPath(
                    taskSettingsMap,
                    "motion_correction_settings",
                    "motionCor2",
                    "eer_sampling"
            );
            Integer eerFraction = MovieDelegateValueHelper.intPath(
                    taskSettingsMap,
                    "motion_correction_settings",
                    "motionCor2",
                    "eer_fraction"
            );
            Double pixelSize = MovieDelegateValueHelper.doublePath(taskDatasetMap, "taskDataSetSetting", "p_size");
            Double accelerationKv = MovieDelegateValueHelper.doublePath(taskSettingsMap, "acceleration_kv");
            String gainFile = MovieDelegateValueHelper.textPath(taskDatasetMap, "gain0", "usable_path");
            Integer sections = resolveSections(execution);
            Double totalDose = MovieDelegateValueHelper.doublePath(taskDatasetMap, "taskDataSetSetting", "total_dose_per_movie");
            Double fmDose = (sections != null && sections > 0 && totalDose != null) ? totalDose / sections : null;

            String motionCommand = buildMotionCommand(
                    movieFilePath,
                    outputMrc,
                    logPrefix,
                    microscope,
                    gainFile,
                    binning,
                    patch,
                    pixelSize,
                    accelerationKv,
                    fmDose,
                    eerSampling,
                    eerFraction,
                    movieFileName,
                    motionDir
            );
            String pngCommand = "mrc_png -i " + quote(dwFile) + " -o " + quote(previewPng);
            String subtractionCommand = "subtarction --input " + quote(outputMrc) + " --output " + quote(subtractionOutput);

            data.put("motionCommand", motionCommand);
            data.put("motionPngCommand", pngCommand);
            data.put("motionSubtractionCommand", subtractionCommand);
            data.put("motionOutputFile", outputMrc);
            data.put("motionDwFile", dwFile);
            data.put("motionDwsFile", dwsFile);
            data.put("motionLogFile", logPrefix);
            data.put("motionLocalLogFile", localLog);
            data.put("motionRigidLogFile", rigidLog);
            if( StringUtils.hasText(gainFile) ) {
                data.put("motionGainFile", gainFile);
            }
            data.put("command", motionCommand + " && " + pngCommand + " && " + subtractionCommand);
            data.put("slurmCmd", "Sbatch");
            data.put("slurm_job_name", sanitizeJobName("motion_" + movieFileName));
            data.put("slurm_output_file", logPrefix + ".out");
            data.put("slurm_error_file", logPrefix + ".err");
            putIfPresent(data, "slurm_partition", execution.getVariable("motionSlurmPartition"));
            putIfPresent(data, "slurm_time", execution.getVariable("motionSlurmTime"));
            putIfPresent(data, "slurm_gres", execution.getVariable("motionSlurmGres"));
            putIfPresent(data, "slurm_task_num", execution.getVariable("motionSlurmTaskNum"));

            variableService.writeData(execution, data);
            SbatchConfig sbatch = MovieSlurmConfigSupport.buildConfig(
                    execution,
                    String.valueOf(data.get("slurm_job_name")),
                    String.valueOf(data.get("slurm_output_file")),
                    String.valueOf(data.get("slurm_error_file")),
                    "slurm_partition",
                    "slurm_time",
                    "slurm_gres",
                    "slurm_task_num"
            );

            return slurmTaskManager.submitSlurmJob(execution, sbatch).thenAccept(job -> applySlurmJob(execution, job));
        } catch( Exception ex ) {
            variableService.writeExceptionData(
                    execution,
                    ex,
                    ex instanceof MovieRetryableException,
                    ex instanceof MovieFatalException
            );
            throw ex;
        }
    }

    private static boolean resolveForceReset(DelegateExecution execution, Map<String, Object> movieMap) {
        Object forceResetVar = execution.getVariable("forceReset");
        if( forceResetVar instanceof Boolean b ) {
            return b;
        }
        return MovieDelegateValueHelper.bool(movieMap, "forceReset");
    }

    private static boolean resolveAlreadyDone(DelegateExecution execution) {
        Double predictDose = MovieDelegateValueHelper.doubleValue(execution.getVariable("motionPredictDose"));
        if( predictDose != null ) {
            return true;
        }
        return MovieDelegateValueHelper.readNestedDouble(execution, "motion", "predict_dose") != null;
    }

    private static String resolveMovieFileName(Map<String, Object> movieMap, String movieFilePath) {
        String fileName = MovieDelegateValueHelper.text(movieMap, "file_name");
        if( StringUtils.hasText(fileName) ) {
            return fileName;
        }
        return new File(movieFilePath).getName();
    }

    private static String resolveWorkDir(DelegateExecution execution, String variableName, String fallback) {
        Object value = execution.getVariable(variableName);
        if( value != null && StringUtils.hasText(String.valueOf(value)) ) {
            return String.valueOf(value);
        }
        return new File("work", fallback).getPath();
    }

    private static Integer resolveSections(DelegateExecution execution) {
        Double sections = MovieDelegateValueHelper.readNestedDouble(execution, "mrcMetadata", "sections");
        return sections == null ? null : sections.intValue();
    }

    private static String buildMotionCommand(
            String input,
            String output,
            String logPrefix,
            String microscope,
            String gainFile,
            Integer binning,
            Integer patch,
            Double pixelSize,
            Double accelerationKv,
            Double fmDose,
            Integer eerSampling,
            Integer eerFraction,
            String movieFileName,
            String motionDir
    ) {
        StringBuilder cmd = new StringBuilder("MotionCor2");
        if( "Titan3_falcon".equals(microscope) ) {
            cmd.append(" -InEer ").append(quote(input));
            if( eerSampling != null ) {
                cmd.append(" -EerSampling ").append(eerSampling);
            }
            if( eerFraction != null && eerFraction > 0 ) {
                String fractionFile = new File(motionDir, movieFileName + ".fraction").getAbsolutePath();
                cmd.append(" -FmIntFile ").append(quote(fractionFile));
            }
        } else {
            cmd.append(" -InTiff ").append(quote(input));
            if( fmDose != null ) {
                cmd.append(" -FmDose ").append(fmDose);
            }
        }
        if( StringUtils.hasText(gainFile) ) {
            cmd.append(" -Gain ").append(quote(gainFile));
        }
        cmd.append(" -OutMrc ").append(quote(output));
        if( binning != null ) {
            cmd.append(" -FtBin ").append(binning);
        }
        if( patch != null ) {
            cmd.append(" -Patch ").append(patch).append(" ").append(patch);
        }
        if( pixelSize != null ) {
            cmd.append(" -PixSize ").append(pixelSize);
        }
        if( accelerationKv != null ) {
            cmd.append(" -kV ").append(accelerationKv);
        }
        cmd.append(" -LogFile ").append(quote(logPrefix));
        cmd.append(" -LogDir ").append(quote(new File(output).getParentFile().getAbsolutePath()));
        return cmd.toString();
    }

    private static String quote(String txt) {
        return "\"" + txt + "\"";
    }

    private static void putIfPresent(Map<String, Object> data, String key, Object value) {
        if( value == null ) {
            return;
        }
        String txt = String.valueOf(value);
        if( !txt.isBlank() ) {
            data.put(key, value);
        }
    }

    private static String sanitizeJobName(String raw) {
        String s = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    private static void applySlurmJob(DelegateExecution execution, SlurmJob job) {
        execution.setVariable("slurmJobId", job.getJobId());
        execution.setVariable("slurmJobName", job.getJobName());
        execution.setVariable("sbatchFilePath", job.getSbatchFilePath());
        execution.setVariable("outputFilePath", job.getOutputFilePath());
        execution.setVariable("errorFilePath", job.getErrorFilePath());
    }
}
