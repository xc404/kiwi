package com.cryo.task.tilt.export;

import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.cryo.model.export.ExportTask;
import com.cryo.service.FilePathService;
import com.cryo.task.engine.BaseContext;
import com.cryo.task.engine.Handler;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.StepResult;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.export.MDocExportContext;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.support.ExportSupport;
import com.cryo.task.tilt.MDocContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MDocExportHandler implements Handler<MDocExportContext>
{
    private final FilePathService filePathService;
    private final ExportSupport exportSupport;
    @Override
    public HandlerKey support() {
        return HandlerKey.MDOC_EXPORT;
    }

    @Override
    public StepResult handle(MDocExportContext context) {

        Task task = context.getTask();
        String path = context.getMDoc().getPath();
        ExportTask exportTask = context.getExportTask();
        File outputDir = this.filePathService.getTaskOutputDir(task,exportTask.getOutputDir());
        copy(context,path,outputDir);
        return StepResult.success("export mdoc success");
    }

    private void copy(MDocExportContext movieContext, String src, File destDir) {
        File srcFile = new File(src);
        File destFile = new File(destDir, srcFile.getName());
        if( destFile.exists() && ! movieContext.forceReset()) {
            try {
                if( FileUtils.checksumCRC32(srcFile) == FileUtils.checksumCRC32(destFile) ) {
                    log.info("{}, file not change, Skip copy {}",  destFile.getAbsolutePath(), srcFile.getName());
                    return;
                }
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
        }
        exportSupport.copyToUser(movieContext.getTask(), srcFile, destDir);
    }
}
