package com.cryo.task.export.handler;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.common.error.FatalException;
import com.cryo.common.error.RetryException;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.MovieResultRepository;
import com.cryo.model.Movie;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportTask;
import com.cryo.model.settings.ExportSettings;
import com.cryo.service.FilePathService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.export.MovieExportContext;
import com.cryo.task.movie.handler.ctf.EstimationResult;
import com.cryo.task.movie.handler.motion.MotionResult;
import com.cryo.task.movie.handler.wait.AsyncHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MotionAndCtfExportHandler extends SlurmExportSupport<MovieExportContext> implements AsyncHandler<MovieExportContext>
{

    private final FilePathService pathService;
    public static final int minSpace = 100 * 1024 * 1024;
    private final MovieRepository movieRepository;
    private final MovieResultRepository movieResultRepository;

    @Override
    public HandlerKey support() {
        return HandlerKey.MotionAndCtfExport;
    }

    public StepResult handle(MovieExportContext movieContext) {

        Task task = movieContext.getTask();
        ExportTask exportTask = movieContext.getExportTask();
        String outputDir = exportTask.getOutputDir();
        ExportSettings exportSettings = exportTask.getExportSettings();
        if( !exportSettings.isExportMotion() && !exportSettings.isExportCTF() ) {
            return StepResult.success("No export");
        }
        try {
            ExportMovie exportMovie = movieContext.getInstance();
            Movie movie = this.movieRepository.findByDataId(task.getId(), exportMovie.getData_id()).orElseThrow();
            if( movie.getCurrent_step().getKey() != HandlerKey.FINISHED ) {
                return StepResult.waitCondition("wait for movie completed");
            }
            MovieResult result = this.movieResultRepository.findByDataId(exportMovie.getData_id(), task.getDefault_config_id()).orElseThrow();


            Map<String, String> copyResult = addCopyFiles(movieContext, result, exportSettings, task, outputDir);

            StepResult execute = execute(movieContext);

            execute.getData().putAll(copyResult);

            return execute;
        } catch( Exception e ) {
            if( !(e instanceof FatalException) ) {

                throw new RetryException(e);
            }
            throw ExceptionUtil.wrapRuntime(e);
        }
    }

    private Map<String, String> addCopyFiles(MovieExportContext movieContext, MovieResult result, ExportSettings exportSettings, Task task, String outputDir) throws IOException {
        TaskStep exportSlurm = TaskStep.of(movieContext.getCurrentStep().getStep(), this.support());


        MotionResult motionCorrection = result.getMotion();
        Map<String, String> copyResult = new HashMap<>();
        if( exportSettings.isExportMotion() ) {
            File file = new File(motionCorrection.getNo_dw().getPath());
            String s = addCopyFile(movieContext, file, pathService.getTaskOutputDir(task, outputDir, "non_dw_motion"));
            copyResult.put(FileNameUtil.getPrefix(file), s);
        }
        if( exportSettings.isExportMotionDw() ) {

            File file = new File(motionCorrection.getDw().getPath());
            String s = addCopyFile(movieContext, file, pathService.getTaskOutputDir(task, outputDir, "dw_motion"));
            copyResult.put(FileNameUtil.getPrefix(file), s);
        }

        if( exportSettings.isExportCTF() ) {
            String path = "ctf";
            EstimationResult ctf = result.getCtfEstimation();
            File ctfMrc = new File(ctf.getOutputFile());
            File ctfLog = new File(ctf.getAvrotFile());
            String cftRe = addCopyFile(movieContext, ctfMrc, pathService.getTaskOutputDir(task, outputDir, path));
            String cftLog = addCopyFile(movieContext, ctfLog, pathService.getTaskOutputDir(task, outputDir, path));

            copyResult.put(FileNameUtil.getPrefix(ctfMrc), cftRe);
            copyResult.put(FileNameUtil.getPrefix(cftLog), cftLog);
        }
        return copyResult;
    }

//    private void addCopySlurm(MovieExportContext movieContext, String cmd, String src, File destDir) {
//        Task task = movieContext.getTask();
//        Movie movie = movieContext.getInstance();
//        TaskStep exportSlurm = TaskStep.of(movieContext.getCurrentStep().getStep(), this.support());
//        File srcFile = new File(src);
//        File destFile = new File(destDir, srcFile.getName());
//        if( destFile.exists() && !movieContext.forceReset() ) {
//            try {
//                if( FileUtils.checksumCRC32(srcFile) == FileUtils.checksumCRC32(destFile) ) {
//                    log.info("{}, file not change, Skip copy {}", destFile.getAbsolutePath(), srcFile.getName());
//                    return;
//                }
//            } catch( IOException e ) {
//                throw new RuntimeException(e);
//            }
//        }
//        movie.addCmd(cmd, exportSlurm, exportSupport.copyToUserShell(task, src, destDir).toCmdStr());
//
//    }
}
