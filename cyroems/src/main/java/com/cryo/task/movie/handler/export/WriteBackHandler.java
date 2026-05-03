//package com.cryo.task.movie.handler.export;
//
//import cn.hutool.core.exceptions.ExceptionUtil;
//import com.cryo.common.error.FatalException;
//import com.cryo.common.error.RetryException;
//import com.cryo.model.Movie;
//import com.cryo.model.MovieImage;
//import com.cryo.model.MovieResult;
//import com.cryo.model.Task;
//import com.cryo.service.FilePathService;
//import com.cryo.service.cmd.UserSpace;
//import com.cryo.task.engine.Handler;
//import com.cryo.task.engine.HandlerKey;
//import com.cryo.task.engine.StepResult;
//import com.cryo.task.engine.TaskStep;
//import com.cryo.task.movie.MovieCleaner;
//import com.cryo.task.movie.MovieContext;
//import com.cryo.task.movie.handler.ctf.EstimationResult;
//import com.cryo.task.movie.handler.motion.MotionResult;
//import com.cryo.task.movie.handler.slurm.SlurmResult;
//import com.cryo.task.movie.handler.slurm.SlurmSupport;
//import com.cryo.task.support.ExportSupport;
//import com.cryo.task.utils.TaskUtils;
//import lombok.Data;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.commons.io.FileUtils;
//import org.springframework.stereotype.Service;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.util.List;
//import java.util.Map;
//
//@Slf4j
//@Data
//@RequiredArgsConstructor
//@Service
//public class WriteBackHandler implements Handler<MovieContext>
//{
//    private final FilePathService pathService;
//    private final ExportSupport exportSupport;
//    private final MovieCleaner movieCleaner;
//    private final SlurmSupport slurmSupport;
//    private final UserSpace userSpace;
//    public static final int minSpace = 100 * 1024 * 1024;
//
//    @Override
//    public HandlerKey support() {
//        return HandlerKey.WriteBack;
//    }
//
//    @Override
//    public StepResult handle(MovieContext movieContext) {
//
//        Task task = movieContext.getTask();
//
//        try {
//            MovieResult result = movieContext.getResult();
//            MotionResult motionCorrection = result.getMotion();
//            TaskStep exportSlurm = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.WriteBack);
//            String path = "motion";
//            addCopySlurm(movieContext, "export_motion_no_dw", motionCorrection.getNo_dw().getPath(), pathService.getOutputDir(task, path, "non-DW"), true);
//            addCopySlurm(movieContext, "export_motion_dw", motionCorrection.getDw().getPath(), pathService.getOutputDir(task, path, "DW"), true);
//
//
//            //write back
//            addCopySlurm(movieContext, "write_back_motion_no_dw", motionCorrection.getNo_dw().getPath(), pathService.getWriteBackDir(movieContext, "base_motion_output", "non"), false);
//
//            addCopySlurm(movieContext, "write_back_motion_dw",
//                    motionCorrection.getDw().getPath(), pathService.getWriteBackDir(movieContext, "base_motion_output", "DW"), false);
//
//            addCopySlurm(movieContext, "write_back_local_motion",
//                    motionCorrection.getLocal_motion().getPath(), pathService.getWriteBackDir(movieContext, "base_motion_output", "motion"), false);
//            addCopySlurm(movieContext, "write_back_rigid_motion",
//                    motionCorrection.getRigid_motion().getPath(), pathService.getWriteBackDir(movieContext, "base_motion_output", "motion"), false);
/// /
//            EstimationResult ctfEstimation = result.getCtfEstimation();
//            addCopySlurm(movieContext, "write_back_ctf_file",
//                    ctfEstimation.getOutputFile(), pathService.getWriteBackDir(movieContext, "base_ctf_output", "data"), false);
//            addCopySlurm(movieContext, "write_back_ctf_avrot_log_file",
//                    ctfEstimation.getAvrotFile(), pathService.getWriteBackDir(movieContext, "base_ctf_output", "output"), false);
//            addCopySlurm(movieContext, "write_back_ctf_log_file",
//                    ctfEstimation.getLogFile(), pathService.getWriteBackDir(movieContext, "base_ctf_output", "output"), false);
////
//            Map<MovieImage.Type, MovieImage> images = movieContext.getResult().getImages();
////
//            addCopySlurm(movieContext, "write_back_motion_png",
//                    images.get(MovieImage.Type.motion_mrc).getPath(), pathService.getWriteBackDir(movieContext, "thumbnails"), false);
//            addCopySlurm(movieContext, "write_back_motion_patch_png",
//                    images.get(MovieImage.Type.patch_log).getPath(), pathService.getWriteBackDir(movieContext, "thumbnails"), false);
//            addCopySlurm(movieContext, "write_back_ctf_png",
//                    images.get(MovieImage.Type.ctf).getPath(), pathService.getWriteBackDir(movieContext, "thumbnails"), false);
//
//            StepResult handle = this.slurmSupport.handle(movieContext, exportSlurm);
//            SlurmResult o = (SlurmResult) handle.getData().get(SlurmSupport.SLURM_RESULT);
//            if( o != null ) {
//                String logFile = o.getLogFile();
//                TaskUtils.checkFileExist(logFile);
//                List<String> lines = FileUtils.readLines(new File(logFile), StandardCharsets.UTF_8);
//                for( String line : lines ) {
//                    if( line.contains("Disk quota exceeded") ) {
//                        throw new FatalException("No space");
//                    }
//                    if( line.contains("source file not found") ) {
//                        throw new RetryException(line);
//                    }
//                }
//            }
//            return handle;
//
//        } catch( Exception e ) {
//            if( !(e instanceof FatalException) ) {
//
//                throw new RetryException(e);
//            }
//            throw ExceptionUtil.wrapRuntime(e);
//        }
//    }
//
//    private void addCopySlurm(MovieContext movieContext, String cmd, String src, File destDir, boolean toUser) {
//        Task task = movieContext.getTask();
//        Movie movie = movieContext.getMovie();
//        TaskStep exportSlurm = TaskStep.of(movieContext.getCurrentStep().getStep(), HandlerKey.WriteBack);
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
//        if( toUser ) {
//            movie.addCmd(cmd, exportSlurm, exportSupport.copyToUserShell(task, src, destDir).toCmdStr());
//        } else {
//            movie.addCmd(cmd, exportSlurm, exportSupport.writeBackShell(srcFile, destDir).toCmdStr());
//        }
//
//    }
//}
