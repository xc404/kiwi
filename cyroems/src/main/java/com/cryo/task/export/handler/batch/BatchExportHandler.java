package com.cryo.task.export.handler.batch;

import cn.hutool.core.collection.ListUtil;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.export.ExportSummary;
import com.cryo.model.export.ExportTask;
import com.cryo.model.settings.ExportSettings;
import com.cryo.service.ExportMovieService;
import com.cryo.service.FilePathService;
import com.cryo.service.GainService;
import com.cryo.service.TaskService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.support.ExportSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class BatchExportHandler
{
    private int batchSize = 400;
    private Map<String, MovieDataset> datasetMap;

    public enum BatchType
    {
        MOVIE,
        MDOC,
        GAIN,
        MOTION,
        DW_MOTION,
        CTF
    }

    private final BatchExportSupport batchExportSupport;
    private final ExportTaskRepository exportTaskRepository;
    private final MovieDataSetRepository movieDataSetRepository;
    private final MDocRepository mDocRepository;
    private final FilePathService pathService;
    private final SoftwareService softwareService;
    private final ExportSupport exportSupport;
    private final Task task;
    private final ExportTask exportTask;
    private ExportSummary exportSummary;
    private final MovieResultRepository movieResultRepository;
    private List<MovieResult> movieResults;
    private List<MovieDataset> movieDatasets;
    private List<MDoc> mDocs;
    private final GainService gainService;
    private final TaskService taskService;
    private final ExportMovieService exportMovieService;

    public BatchExportHandler(ApplicationContext applicationContext, Task task, ExportTask exportTask) {
        this.batchExportSupport = applicationContext.getBean(BatchExportSupport.class);
        this.softwareService = applicationContext.getBean(SoftwareService.class);
        this.exportSupport = applicationContext.getBean(ExportSupport.class);
        this.exportTaskRepository = applicationContext.getBean(ExportTaskRepository.class);
        this.movieDataSetRepository = applicationContext.getBean(MovieDataSetRepository.class);
        this.pathService = applicationContext.getBean(FilePathService.class);
        this.movieResultRepository = applicationContext.getBean(MovieResultRepository.class);
        this.gainService = applicationContext.getBean(GainService.class);
        this.mDocRepository = applicationContext.getBean(MDocRepository.class);
        this.taskService = applicationContext.getBean(TaskService.class);
        this.task = task;
        this.exportTask = exportTask;
        this.exportMovieService = applicationContext.getBean(ExportMovieService.class);
    }

    private void init() {
        Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(task.getTaskSettings().getDataset_id()), task.getTaskSettings().getDataset_id()));
        this.movieDatasets = this.movieDataSetRepository.findByQuery(query);
        this.movieDatasets = movieDatasets.stream().sorted(Comparator.comparing(MovieDataset::getMtime)).toList();
        this.datasetMap = movieDatasets.stream().collect(Collectors.toMap(MovieDataset::getPath, Function.identity()));
        this.mDocs = this.mDocRepository.findByQuery(query);

        Query movieResultQuery = Query.query(Criteria.where("config_id").is(task.getDefault_config_id()).and("task_data_id").is(task.getTaskSettings().getDataset_id()));
        this.movieResults = this.movieResultRepository.findByQuery(movieResultQuery);
//        query.addCriteria(Criteria.where("config_id").is(task.getDefault_config_id()));
        this.exportSummary = new ExportSummary();
        log.info("Exporting task {}", task.getTask_name());
        if( exportTask.getExportSettings().isExportRawMovie() ) {
            exportSummary.setMovie(new ExportSummary.Summary(movieDatasets.size(), 0, 0, 0));
        }
        if( exportTask.getExportSettings().isExportGain() ) {
            exportSummary.setGain(new ExportSummary.Summary(2, 2, 0, 0));
        }
        if( exportTask.getExportSettings().isExportMotionDw() ) {
            exportSummary.setDw(new ExportSummary.Summary(movieResults.size(), 0, 0, 0));
        }
        if( exportTask.getExportSettings().isExportMotion() ) {
            exportSummary.setNoDw(new ExportSummary.Summary(movieResults.size(), 0, 0, 0));
        }
        if( exportTask.getExportSettings().isExportCTF() ) {
            exportSummary.setCtf(new ExportSummary.Summary(movieResults.size(), 0, 0, 0));
        }
        if( task.getIs_tomo() && exportTask.getExportSettings().isExportMdoc() ) {
            exportSummary.setMdoc(new ExportSummary.Summary(mDocs.size(), 0, 0, 0));
        }


        saveSummary();
    }

    private void createExportMdocs(List<MDoc> mDocs) {
        mDocs.forEach(m -> {
            this.exportMovieService.createExportMdoc(exportTask, m, HandlerKey.FINISHED);
        });
    }

    private void updateExportMovies(List<MovieDataset> movieDatasets) {
        movieDatasets.forEach(m -> {
            this.exportMovieService.createExportMovie(exportTask, m, HandlerKey.FINISHED);
        });
    }

    private void createExportMovies(List<String> batchFiles) {

        batchFiles.forEach(file -> {
            MovieDataset movieDataset = datasetMap.get(file);
            this.exportMovieService.createExportMovie(exportTask, movieDataset, HandlerKey.FINISHED);
        });
    }


    public void export() {
        try {
            init();
            ExportSettings exportSettings = this.exportTask.getExportSettings();
            if( exportSettings.isExportGain() ) {

                this.exportGain();
            }
            if( exportSettings.isExportRawMovie() ) {

                this.exportMovies();
            }
            if( exportSettings.isExportMotion() ) {

                this.exportMotions();
            }
            if( exportSettings.isExportMotionDw() ) {

                this.exportDwMotions();
            }

            if( exportSettings.isExportCTF() ) {

                this.exportCtf();
            }
            if( task.getIs_tomo() ) {
                if( exportSettings.isExportMdoc() ) {
                    this.exportMdoc();
                    createExportMdocs(mDocs);
                }
            }
            log.info("Exporting task {} complete", task.getTask_name());
            this.updateExportMovies(movieDatasets);
            this.exportTask.setErrorSummary("Export completed successfully");
            this.exportTaskRepository.save(exportTask);
            this.taskService.completeExportTask(exportTask);
            if( exportSettings.isExportRawMovie()){
                this.exportSummary.getMovie().setCompleted(this.exportSummary.getMovie().getTotal());
            }
            if(exportSettings.isExportVfm()){
                this.exportSummary.getVfm().setCompleted(this.exportSummary.getVfm().getTotal());
            }
            if(exportSettings.isExportMdoc()){
                this.exportSummary.getMdoc().setCompleted(this.exportSummary.getMdoc().getTotal());
            }
            if(exportSettings.isExportGain()){
                this.exportSummary.getGain().setCompleted(this.exportSummary.getGain().getTotal());
            }
            if(exportSettings.isExportCTF()){
                this.exportSummary.getCtf().setCompleted(this.exportSummary.getCtf().getTotal());
            }
            if(exportSettings.isExportMotion()){
                this.exportSummary.getNoDw().setCompleted(this.exportSummary.getNoDw().getTotal());
            }
            if(exportSettings.isExportMotionDw()){
                this.exportSummary.getDw().setCompleted(this.exportSummary.getDw().getTotal());
            }

            this.saveSummary();

        } catch( Exception e ) {
            log.error("Error exporting task {}", task.getTask_name(), e);
            exportTask.setErrorSummary(e.getMessage());
            this.exportTaskRepository.save(exportTask);
            this.taskService.setExportTaskStatus(exportTask.getId(), TaskStatus.stopped);
        }
    }

    private void exportGain() {
        this.gainService.exportGain(exportTask);
        this.exportTask.setGainExported(true);
        this.exportSummary.setGain(new ExportSummary.Summary(2, 2, 0, 0));
    }

    public void exportMovies() {
        log.info("Exporting movies");
        if( !this.exportTask.getExportSettings().isExportRawMovie() ) {
            return;
        }
//        int total = movieDatasets.size();
        List<String> files = movieDatasets.stream().map(m -> m.getPath()).toList();
        copyFiles(BatchType.MOVIE, files);
//        this.exportSummary.setMovie(new ExportSummary.Summary(total, total, 0, 0));
//        saveSummary();
    }

    private void saveSummary() {
        log.info("Saving summary {}", this.exportSummary);
        this.exportTaskRepository.setSummary(this.exportTask.getId(), this.exportSummary);
    }


    public void exportMotions() {
        log.info("Exporting motions");
        List<String> files = movieResults.stream().map(m -> Optional.ofNullable(m.getMotion()).map(o -> o.getNo_dw().getPath()).orElse(null)).filter(Objects::nonNull).toList();
        copyFiles(BatchType.MOTION, files, "non_dw_motion");
//        this.exportSummary.setNoDw(new ExportSummary.Summary(files.size(), files.size(), 0, 0));
//        saveSummary();
    }

    public void exportDwMotions() {
        log.info("Exporting dw motions");
        List<String> files = movieResults.stream().map(m -> Optional.ofNullable(m.getMotion()).map(o -> o.getDw().getPath()).orElse(null)).filter(Objects::nonNull).toList();
        copyFiles(BatchType.DW_MOTION, files, "dw_motion");
//        this.exportSummary.setDw(new ExportSummary.Summary(files.size(), files.size(), 0, 0));
//        saveSummary();
    }

    public void exportCtf() {
        log.info("Exporting ctf");
        List<String> logFile = movieResults.stream().map(m -> Optional.ofNullable(m.getCtfEstimation()).map(o -> o.getAvrotFile()).orElse(null)).filter(Objects::nonNull).toList();
        List<String> ctfFiles = movieResults.stream().map(m -> Optional.ofNullable(m.getCtfEstimation()).map(o -> o.getOutputFile()).orElse(null)).filter(Objects::nonNull).toList();
        ArrayList<String> files = new ArrayList<>(logFile);
        files.addAll(ctfFiles);
        copyFiles(BatchType.CTF, files, "ctf");
//        this.exportSummary.setCtf(new ExportSummary.Summary(files.size(), files.size(), 0, 0));
//        saveSummary();
    }

    private void exportMdoc() {
        log.info("Exporting mdocs");
        List<String> files = mDocs.stream().map(m -> m.getPath()).toList();
        copyFiles(BatchType.MDOC, files);
//        this.exportSummary.setCtf(new ExportSummary.Summary(files.size(), files.size(), 0, 0));
//        saveSummary();
    }

    private void copyFiles(BatchType type, List<String> files, String... child) {

        List<List<String>> split = ListUtil.split(files, this.batchSize);
        int total = 0;
        for( int i = 0; i < split.size(); i++ ) {
            List<String> batchFiles = split.get(i);
            if( exportSupport.enabledFileService() ) {
                File taskOutputDir = pathService.getTaskOutputDir(task, exportTask.getOutputDir(), child);
                try {
                    this.batchExportSupport.export(batchFiles, taskOutputDir, task, batchExportSupport.getDefaultBatchSize()).get();
                } catch( InterruptedException | ExecutionException e ) {
                    throw new RuntimeException(e);
                }

            } else {
                copyInShell(type, batchFiles, child, i);
            }
            total += batchFiles.size();
            saveStatus(type, total);

        }


    }

    private void saveStatus(BatchType type, int processed) {


        switch( type ) {
            case MOVIE:
                this.exportSummary.setMovie(new ExportSummary.Summary(this.exportSummary.getMovie().getTotal(), processed, 0, 0));
                break;
            case MDOC:
                this.exportSummary.setMdoc(new ExportSummary.Summary(this.exportSummary.getMdoc().getTotal(), processed, 0, 0));
                break;
            case GAIN:
                this.exportSummary.setGain(new ExportSummary.Summary(this.exportSummary.getGain().getTotal(), processed, 0, 0));
                break;
            case MOTION:
                this.exportSummary.setNoDw(new ExportSummary.Summary(this.exportSummary.getNoDw().getTotal(), processed, 0, 0));
                break;
            case DW_MOTION:
                this.exportSummary.setDw(new ExportSummary.Summary(this.exportSummary.getDw().getTotal(), processed, 0, 0));
                break;
            case CTF:
                this.exportSummary.setCtf(new ExportSummary.Summary(this.exportSummary.getCtf().getTotal(), processed, 0, 0));
                break;
        }
        this.saveSummary();
//        if( type == BatchType.MOVIE ) {
//            createExportMovies(batchFiles);
//        }

    }


    private void copyInShell(BatchType type, List<String> files, String[] child, int batch) {
        File taskOutputDir = pathService.getTaskOutputDir(task, exportTask.getOutputDir(), child);

        File workDir = pathService.getWorkDir(task, "export");
        File sourceFile = new File(workDir, exportTask.getTaskWorkDir() + "_" + type + "_" + batch + ".txt");
        try {
            FileUtils.writeLines(sourceFile, files);
            exportSupport.toSelf(sourceFile);
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
//        this.softwareService.copy_and_change_own(sourceFile.getAbsolutePath(), taskOutputDir.getAbsolutePath(), task.getOwner(), task.getGroup_name());
        try {
            exportSupport.copyToUserShell(task, sourceFile.getAbsolutePath(), taskOutputDir).startAndWait();
        } catch( Exception e ) {
            throw new RuntimeException(e);
        }
    }

}
