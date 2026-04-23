package com.cryo.task.clean;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.MDocResultRepository;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.TaskDataSetRepository;
import com.cryo.model.MDocResult;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.model.dataset.TaskDataset;
import com.cryo.service.FilePathService;
import com.cryo.task.support.ExportSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCleaner
{
    private final TaskRepository taskRepository;
    private final MovieResultRepository movieResultRepository;
    private final MDocResultRepository mDocResultRepository;
    private final FilePathService filePathService;
    private final ExportSupport exportSupport;
    private final TaskDataSetRepository taskDatasetRepository;

    @Scheduled(cron = "0 0 0/10 * * ?")
    public void clean() {
        List<Task> tasks = this.taskRepository.findByQuery(Query.query(Criteria.where("created_at").lt(DateUtils.addDays(new Date(), -7))
                .and("cleaned").ne(true)));
        tasks.forEach(t -> {
            try {
                cleanTask(t);
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
        });


        //TODO:
    }

    public void cleanTask(Task t) {
        log.info("clean task: " + t.getTask_name());
        if( t.getCreated_at().after(DateUtils.addDays(new Date(), -15)) ) {
            throw new RuntimeException("not history task");
        }

        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(Query.query(Criteria.where("task_id").is(t.getId())));
//
        movieResults.forEach(m ->
                        copyVfmPng(m)
//                cleanMovie(m)
        );
//
        List<MDocResult> mDocResults = this.mDocResultRepository.findByQuery(Query.query(Criteria.where("task_id").is(t.getId())));
//
        mDocResults.forEach(movieResult -> {
//            cleanMdoc(movieResult);
            copyMdocRebuildResult(movieResult);
        });
        TaskDataset taskDataset = this.taskDatasetRepository.findById(t.getTaskSettings().getDataset_id()).orElseThrow();
        String name = FileNameUtil.getName(taskDataset.getMovie_path());
        String taskRoot = name + "_" + taskDataset.getId();
        File workDir = this.filePathService.getWorkDir(taskRoot);
        cleanWorkDir(workDir);
        t.setCleaned(true);
        this.taskRepository.save(t);
        log.info("clean task: " + t.getTask_name() + " done");
    }


    private void cleanWorkDir(File workDir) {
        File[] files = workDir.listFiles();
        if( files == null ) {
            return;
        }
        for( File file : files ) {
            if( file.isDirectory() ) {
                cleanConfigDir(file);
            } else {
                FileUtils.deleteQuietly(file);
            }

        }
    }

    private void cleanConfigDir(File file) {
        if( file.getName().equals("thumbnails") ) {
            return;
        }
        File[] files = file.listFiles(f -> !f.getName().equals("thumbnails"));
        if( files == null ) {
            return;
        }
        Arrays.stream(files).forEach(f -> {
            if( f.isDirectory() ) {
                try {
                    FileUtils.deleteDirectory(f);
                } catch( IOException e ) {
                    log.error(e.getMessage(), e);
                }
            } else {
                if( !f.getName().endsWith(".png") ) {
                    FileUtils.deleteQuietly(f);
                }
            }
        });
    }

    private void copyVfmPng(MovieResult m) {

        Optional.ofNullable(m.getVfmResult()).map(vfmResult -> vfmResult.getPngFile())
                .ifPresent(p -> {
                    if( p.contains("/vfm/") ) {
                        File srcFile = new File(p);
                        if( !srcFile.exists() ) {
                            return;
                        }
                        String path = p.replace("/vfm/", "/thumbnails/");
                        File dest = new File(path);
                        try {
                            FileUtils.copyFile(new File(p), dest);
                            exportSupport.toSelf(dest);
                            m.getVfmResult().setPngFile(dest.getAbsolutePath());
                            this.movieResultRepository.save(m);
                        } catch( Exception e ) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    private void copyMdocRebuildResult(MDocResult m) {

        Optional.ofNullable(m.getAlignReconResult()).map(vfmResult -> vfmResult.getAlign_reconOutput())
                .ifPresent(p -> {
                    if( p.contains("/mdoc/") ) {
                        File src = new File(p);
                        if( !src.exists() ) {
                            return;
                        }
                        File srcDir = src.getParentFile().getParentFile().getParentFile();
                        File dest = new File(new File(srcDir, "thumbnails"), src.getName());
                        try {
                            FileUtils.copyFile(src, dest);
                            exportSupport.toSelf(dest);
                            m.getAlignReconResult().setAlign_reconOutput(dest.getCanonicalPath());
                            this.mDocResultRepository.save(m);
                        } catch( Exception e ) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }


    private void cleanMovie(MovieResult m) {

        //header
        Optional.ofNullable(m.getMrcMetadata()).map(motionResult -> motionResult.getFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));

        //motion
        Optional.ofNullable(m.getMotion()).map(motionResult -> motionResult.getDw()).map(dw -> dw.getPath())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getMotion()).map(motionResult -> motionResult.getNo_dw()).map(dw -> dw.getPath())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getMotion()).map(motionResult -> motionResult.getDws()).map(dw -> dw.getPath())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getMotion()).map(motionResult -> motionResult.getLocal_motion()).map(dw -> dw.getPath())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getMotion()).map(motionResult -> motionResult.getRigid_motion()).map(dw -> dw.getPatch_log_file())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getMotion()).map(motionResult -> motionResult.getLocal_motion()).map(dw -> dw.getPath())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getMotion()).map(motionResult -> motionResult.getRigid_motion()).map(dw -> dw.getPatch_log_file())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));

        //cft
        Optional.ofNullable(m.getCtfEstimation()).map(motionResult -> motionResult.getLogFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getCtfEstimation()).map(motionResult -> motionResult.getOutputFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getCtfEstimation()).map(motionResult -> motionResult.getAvrotFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        //vfm
        Optional.ofNullable(m.getVfmResult()).map(motionResult -> motionResult.getLogFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getVfmResult()).map(motionResult -> motionResult.getOutputFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
    }

    private void cleanMdoc(MDocResult m) {
        //stack
        Optional.ofNullable(m.getStackResult()).map(motionResult -> motionResult.getTitlFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getStackResult()).map(motionResult -> motionResult.getOutputFile())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));

        //coarseAlignrResult
        Optional.ofNullable(m.getCoarseAlignrResult()).map(motionResult -> motionResult.getXftoxgOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getCoarseAlignrResult()).map(motionResult -> motionResult.getNewstackOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getCoarseAlignrResult()).map(motionResult -> motionResult.getTiltxcorrOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));

        //coarseAlignrResult
        Optional.ofNullable(m.getPatchTrackingResult()).map(motionResult -> motionResult.getImodchopcontsOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getPatchTrackingResult()).map(motionResult -> motionResult.getTiltxcorrOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        //seriesAlignResult
        Optional.ofNullable(m.getSeriesAlignResult()).map(motionResult -> motionResult.getTransformOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getSeriesAlignResult()).map(motionResult -> motionResult.getFidXYZOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getSeriesAlignResult()).map(motionResult -> motionResult.getResidualFileOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getSeriesAlignResult()).map(motionResult -> motionResult.getTiltFileOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getSeriesAlignResult()).map(motionResult -> motionResult.getModelFileOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
        Optional.ofNullable(m.getSeriesAlignResult()).map(motionResult -> motionResult.getXAxisTiltOutput())
                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));


        //alignReconResult
//        Optional.ofNullable(m.getAlignReconResult()).map(motionResult -> motionResult.getXfproductOutput())
//                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
//        Optional.ofNullable(m.getAlignReconResult()).map(motionResult -> motionResult.getPatch2imodOutput())
//                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
//        Optional.ofNullable(m.getAlignReconResult()).map(motionResult -> motionResult.getStack1Output())
//                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
//        Optional.ofNullable(m.getAlignReconResult()).map(motionResult -> motionResult.getStack2Output())
//                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
//        Optional.ofNullable(m.getAlignReconResult()).map(motionResult -> motionResult.getXfproductOutput())
//                .ifPresent(p -> FileUtils.deleteQuietly(new java.io.File(p)));
    }

}
