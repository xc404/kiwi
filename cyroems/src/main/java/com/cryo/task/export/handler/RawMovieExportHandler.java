package com.cryo.task.export.handler;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.common.error.FatalException;
import com.cryo.common.error.RetryException;
import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportTask;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.UserSpace;
import com.cryo.service.fileservice.FileService;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.export.MovieExportContext;
import com.cryo.task.movie.MovieCleaner;
import com.cryo.task.movie.handler.slurm.SlurmResult;
import com.cryo.task.movie.handler.slurm.SlurmSupport;
import com.cryo.task.utils.TaskUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Data
@RequiredArgsConstructor
@Service
public class RawMovieExportHandler extends SlurmExportSupport<MovieExportContext> implements Handler<MovieExportContext>
{
    private final FilePathService pathService;
    private final FileService fileService;
    private final MovieCleaner movieCleaner;
    private final UserSpace userSpace;
    //    private final CryosparcCompleteHanlder cryosparcService;
    public static final int minSpace = 100 * 1024 * 1024;


    @Override
    public HandlerKey support() {
        return HandlerKey.RawMovieExport;
    }

    @Override
    public StepResult handle(MovieExportContext movieContext) {

        Task task = movieContext.getTask();
        try {
            ExportMovie movie = movieContext.getInstance();

            ExportTask exportTask = movieContext.getExportTask();

            String file = movie.getFile_path();
            File outputDir = this.pathService.getTaskOutputDir(task, exportTask.getOutputDir());

            String s = super.addCopyFile(movieContext, new File(file), outputDir);

            StepResult stepResult = super.execute(movieContext);

            stepResult.getData().put(FileNameUtil.getPrefix(file), s);
            return stepResult;
        } catch( Exception e ) {
            if( !(e instanceof FatalException) ) {

                throw new RetryException(e);
            }
            throw ExceptionUtil.wrapRuntime(e);
        }
    }

    private StepResult slurmCopy(MovieExportContext movieContext, ExportMovie movie, File outputDir, TaskStep exportSlurm) throws IOException {
        addCopySlurm(movieContext, "export_raw_file", movie.getFile_path(), outputDir);

        StepResult handle = this.slurmSupport.handle(movieContext, exportSlurm);
        SlurmResult o = (SlurmResult) handle.getData().get(SlurmSupport.SLURM_RESULT);
        if( o != null ) {
            String logFile = o.getLogFile();
            TaskUtils.checkFileExist(logFile);
            List<String> lines = FileUtils.readLines(new File(logFile), StandardCharsets.UTF_8);
            for( String line : lines ) {
                if( line.contains("Disk quota exceeded") ) {
                    throw new FatalException("No space");
                }
                if( line.contains("source file not found") ) {
                    throw new RetryException(line);
                }
            }
        }
        return handle;
    }

    private void addCopySlurm(MovieExportContext movieContext, String cmd, String src, File destDir) {
        Task task = movieContext.getTask();
        Movie movie = movieContext.getInstance();
        TaskStep exportSlurm = TaskStep.of(movieContext.getCurrentStep().getStep(), this.support());
        File srcFile = new File(src);
        File destFile = new File(destDir, srcFile.getName());
        if( destFile.exists() && !movieContext.forceReset() ) {
            try {
                if( FileUtils.checksumCRC32(srcFile) == FileUtils.checksumCRC32(destFile) ) {
                    log.info("{}, file not change, Skip copy {}", destFile.getAbsolutePath(), srcFile.getName());
                    return;
                }
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
        }
        movie.addCmd(cmd, exportSlurm, exportSupport.copyToUserShell(task, src, destDir).toCmdStr());

    }
}
