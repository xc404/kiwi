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
        name = "Movie CTF Estimation",
        group = "CryoEMS",
        version = "1.0",
        description = "生成 CTFFIND5 批处理命令及其输出文件与日志路径",
        outputs = {
                @ComponentParameter(
                        key = "ctfCommand",
                        name = "ctfCommand",
                        type = "string",
                        description = "CTFFIND5 主命令"),
                @ComponentParameter(
                        key = "ctfPngCommand",
                        name = "ctfPngCommand",
                        type = "string",
                        description = "ctf 预览图命令"),
                @ComponentParameter(
                        key = "ctfInputFile",
                        name = "ctfInputFile",
                        type = "string",
                        description = "CTFFIND5 输入文件"),
                @ComponentParameter(
                        key = "ctfOutputFile",
                        name = "ctfOutputFile",
                        type = "string",
                        description = "CTFFIND5 输出 mrc 文件"),
                @ComponentParameter(
                        key = "ctfLogFile",
                        name = "ctfLogFile",
                        type = "string",
                        description = "CTFFIND5 log 文件"),
                @ComponentParameter(
                        key = "ctfAvrotFile",
                        name = "ctfAvrotFile",
                        type = "string",
                        description = "CTFFIND5 avrot 文件"),
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
@Component("movieCtfEstimationJavaDelegate")
@ExternalTaskSubscription(topicName = "movie-ctf-estimation", lockDuration = 300000)
public class MovieCtfEstimationJavaDelegate extends AbstractExternalTaskHandler
{
    private final MovieDelegateVariableService variableService;
    private final SlurmTaskManager slurmTaskManager;

    public MovieCtfEstimationJavaDelegate(
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
            if( MovieDelegateValueHelper.bool(execution.getVariable("movieDelegateRollback")) ) {
                Map<String, Object> rollback = new LinkedHashMap<>();
                rollback.put("ctfSkipped", true);
                rollback.put("ctfSkipReason", "rollback_enabled");
                variableService.writeData(execution, rollback);
                return CompletableFuture.completedFuture(null);
            }
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
            String ctfDir = resolveWorkDir(execution, "ctfWorkDir", "ctf");
            String imageDir = resolveWorkDir(execution, "imageWorkDir", "thumbnails");
            boolean forceReset = resolveForceReset(execution, movieMap);
            boolean alreadyDone = resolveAlreadyDone(execution);


            Map<String, Object> data = new LinkedHashMap<>();
            if( alreadyDone && !forceReset ) {
                data.put("ctfSkipped", true);
                data.put("ctfSkipReason", "stigma_x_exists");
                variableService.writeData(execution, data);
                return CompletableFuture.completedFuture(null);
            }

            String ctfInput = resolveMotionInput(execution, movieFilePath);
            String outputFile = new File(ctfDir, movieFileName + "_freq.mrc").getAbsolutePath();
            String logFile = new File(ctfDir, movieFileName + "_freq.txt").getAbsolutePath();
            String avrotFile = new File(ctfDir, movieFileName + "_freq_avrot.txt").getAbsolutePath();
            String pngFile = new File(imageDir, movieFileName + "_freq.png").getAbsolutePath();

            Double pixelSize = resolvePixelSize(taskDatasetMap, taskSettingsMap, taskMap);
            Double accelKv = MovieDelegateValueHelper.doublePath(taskSettingsMap, "acceleration_kv");
            Double spherical = MovieDelegateValueHelper.doublePath(taskSettingsMap, "spherical_aberration");
            Double ampContrast = MovieDelegateValueHelper.doublePath(taskSettingsMap, "amplitude_contrast");
            Double spectrumSize = MovieDelegateValueHelper.doublePath(
                    taskSettingsMap,
                    "ctf_estimation_settings",
                    "ctffind5",
                    "spectrum_size"
            );
            Double minRes = MovieDelegateValueHelper.doublePath(taskSettingsMap, "ctf_estimation_settings", "ctffind5", "min_res");
            Double maxRes = MovieDelegateValueHelper.doublePath(taskSettingsMap, "ctf_estimation_settings", "ctffind5", "max_res");
            Double minDefocus = MovieDelegateValueHelper.doublePath(taskSettingsMap, "ctf_estimation_settings", "ctffind5", "min_defocus");
            Double maxDefocus = MovieDelegateValueHelper.doublePath(taskSettingsMap, "ctf_estimation_settings", "ctffind5", "max_defocus");
            Double defocusStep = MovieDelegateValueHelper.doublePath(
                    taskSettingsMap,
                    "ctf_estimation_settings",
                    "ctffind5",
                    "defocus_step"
            );

            String ctfCommand = buildCtfCommand(
                    ctfInput,
                    outputFile,
                    pixelSize,
                    accelKv,
                    spherical,
                    ampContrast,
                    spectrumSize,
                    minRes,
                    maxRes,
                    minDefocus,
                    maxDefocus,
                    defocusStep
            );
            String ctfPngCommand = "ctf_png -i " + quote(outputFile) + " -o " + quote(pngFile);

            data.put("ctfCommand", ctfCommand);
            data.put("ctfPngCommand", ctfPngCommand);
            data.put("ctfInputFile", ctfInput);
            data.put("ctfOutputFile", outputFile);
            data.put("ctfLogFile", logFile);
            data.put("ctfAvrotFile", avrotFile);
            data.put("ctfPngFile", pngFile);
            data.put("command", ctfCommand + " && " + ctfPngCommand);
            data.put("slurmCmd", "Sbatch");
            data.put("slurm_job_name", sanitizeJobName("ctf_" + movieFileName));
            data.put("slurm_output_file", logFile + ".out");
            data.put("slurm_error_file", logFile + ".err");
            putIfPresent(data, "slurm_partition", execution.getVariable("ctfSlurmPartition"));
            putIfPresent(data, "slurm_time", execution.getVariable("ctfSlurmTime"));
            putIfPresent(data, "slurm_task_num", execution.getVariable("ctfSlurmTaskNum"));
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
        Double stigmaX = MovieDelegateValueHelper.doubleValue(execution.getVariable("ctfStigmaX"));
        if( stigmaX != null ) {
            return true;
        }
        return MovieDelegateValueHelper.readNestedDouble(execution, "ctfEstimation", "stigma_x") != null;
    }

    private static String resolveMotionInput(DelegateExecution execution, String movieFilePath) {
        Object motionOutputFile = execution.getVariable("motionOutputFile");
        if( motionOutputFile != null && StringUtils.hasText(String.valueOf(motionOutputFile)) ) {
            return String.valueOf(motionOutputFile);
        }
        Object motionNoDw = execution.getVariable("motionNoDwPath");
        if( motionNoDw != null && StringUtils.hasText(String.valueOf(motionNoDw)) ) {
            return String.valueOf(motionNoDw);
        }
        return movieFilePath;
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

    private static Double resolvePixelSize(
            Map<String, Object> taskDatasetMap,
            Map<String, Object> taskSettingsMap,
            Map<String, Object> taskMap
    ) {
        Double p = MovieDelegateValueHelper.doublePath(taskDatasetMap, "taskDataSetSetting", "p_size");
        if( p != null ) {
            return p;
        }
        p = MovieDelegateValueHelper.doublePath(taskSettingsMap, "p_size");
        if( p != null ) {
            return p;
        }
        return MovieDelegateValueHelper.doublePath(taskMap, "p_size");
    }

    private static String buildCtfCommand(
            String input,
            String output,
            Double pixelSize,
            Double accelKv,
            Double spherical,
            Double ampContrast,
            Double spectrumSize,
            Double minRes,
            Double maxRes,
            Double minDefocus,
            Double maxDefocus,
            Double defocusStep
    ) {
        StringBuilder cmd = new StringBuilder("ctffind5");
        cmd.append(" -i ").append(quote(input));
        cmd.append(" -o ").append(quote(output));
        appendIfNotNull(cmd, "-p", pixelSize);
        appendIfNotNull(cmd, "-v", accelKv);
        appendIfNotNull(cmd, "-c", spherical);
        appendIfNotNull(cmd, "-a", ampContrast);
        appendIfNotNull(cmd, "-s", spectrumSize);
        appendIfNotNull(cmd, "--rmin", minRes);
        appendIfNotNull(cmd, "--rmax", maxRes);
        appendIfNotNull(cmd, "--dmin", minDefocus);
        appendIfNotNull(cmd, "--dmax", maxDefocus);
        appendIfNotNull(cmd, "--step", defocusStep);
        return cmd.toString();
    }

    private static void appendIfNotNull(StringBuilder cmd, String opt, Double val) {
        if( val == null ) {
            return;
        }
        cmd.append(" ").append(opt).append(" ").append(val);
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
