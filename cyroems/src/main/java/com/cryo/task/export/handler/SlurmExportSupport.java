package com.cryo.task.export.handler;

import com.cryo.common.error.FatalException;
import com.cryo.common.error.RetryException;
import com.cryo.model.Instance;
import com.cryo.model.Task;
import com.cryo.task.engine.BaseContext;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.movie.handler.slurm.SlurmResult;
import com.cryo.task.movie.handler.slurm.SlurmSupport;
import com.cryo.task.support.ExportSupport;
import com.cryo.task.utils.TaskUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j

public abstract class SlurmExportSupport<T extends BaseContext> implements Handler<T>
{

    @Autowired
    @Setter
    protected ExportSupport exportSupport;
    @Autowired
    @Setter
    protected SlurmSupport slurmSupport;


    public String addCopyFile(BaseContext context, File source, File destDir) {

        Task task = context.getTask();
        Instance movie = context.getInstance();
        TaskStep exportSlurm = TaskStep.of(context.getCurrentStep().getStep(), this.support());
//        File srcFile = new File(src);
        String name = source.getName();
        File destFile = new File(destDir, name);
        if( destFile.exists() && !context.forceReset() ) {
            try {
                if( FileUtils.checksumCRC32(source) == FileUtils.checksumCRC32(destFile) ) {
                    log.info("{}, file not change, Skip copy {}", destFile.getAbsolutePath(), name);
                    return "file skipped";
                }
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
        }


        if( exportSupport.enabledFileService() ) {
            return this.exportSupport.copyToUser(task, source, destDir);
        } else {
            String cmdStr = exportSupport.copyToUserShell(task, source.toString(), destDir).toCmdStr();
            movie.addCmd(name, exportSlurm, cmdStr);
            return cmdStr;
        }
    }

    public StepResult execute(BaseContext context) {

        if( exportSupport.enabledFileService() ) {
            return StepResult.success("file copied");
        }

        TaskStep exportSlurm = TaskStep.of(context.getCurrentStep().getStep(), this.support());
        StepResult handle = this.slurmSupport.handle(context, exportSlurm);
        SlurmResult o = (SlurmResult) handle.getData().get(SlurmSupport.SLURM_RESULT);
        if( o != null ) {
            String logFile = o.getLogFile();
            TaskUtils.checkFileExist(logFile);
            List<String> lines = null;
            try {
                lines = FileUtils.readLines(new File(logFile), StandardCharsets.UTF_8);
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
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

}
