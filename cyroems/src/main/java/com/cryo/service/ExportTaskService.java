package com.cryo.service;

import com.cryo.ctl.ExportCtl;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.MDocRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Task;
import com.cryo.model.TaskStatus;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.export.ExportSummary;
import com.cryo.model.export.ExportTask;
import com.cryo.model.settings.ExportSettings;
import com.cryo.service.session.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ExportTaskService
{
    public static final long motionMovieSize = 94280704;
    public static final long ctfMovieSize = 1049600;
    //    private String output_dir = "/home/cryosparc-data";
    @Value("${app.cryosparc.output_dir:/home/cryosparc-data}")
    private String HOME_CRYOSPARC_DATA = "/home/cryosparc-data";
    private final ExportTaskRepository exportTaskRepository;
    private final TaskRepository taskRepository;
    private final MovieDataSetRepository movieDataSetRepository;
    private final MDocRepository mDocRepository;
    private final ExportMovieService exportMovieService;
    private final SessionService sessionService;
    private final TaskService taskService;

    public void createDefaultExportTask(Task task) {
        createDirExportTask(task);
        if( task.getTaskSettings().getCryosparcSettings().isEnabled() ) {
            createCryosparcExportTask(task);
        }

    }

    private void createCryosparcExportTask(Task task) {
        ExportTask exportTask = new ExportTask();
        exportTask.setTask_id(task.getId());
        exportTask.setId(task.getId() + "_cryosparc_export");
        exportTask.setExportSettings(task.getTaskSettings().getExportSettings());
//        File file = new File(getDefaultCryosparcOutputDir().getAbsolutePath(), task.getTask_name());
//        int i =1;
//        while( file.exists() ) {
//            file = new File(getDefaultCryosparcOutputDir().getAbsolutePath(), task.getTask_name() + "_" + (i++));
//        }
//        exportTask.setOutputDir(task.getTaskSettings().getCryosparcSettings().getOutputDir());
//        exportTask.getExportSettings().setOutputDir(exportTask.getOutputDir());
        exportTask.setCryosparcSettings(task.getTaskSettings().getCryosparcSettings());
        exportTask.setDefault(true);
        exportTask.setCryosparc(true);
        exportTask.setName("Cryosparc Default");
        exportTask.setStatus(TaskStatus.running);
        exportTask.setCreated_at(new Date());
        checkOutput(exportTask);
        this.exportTaskRepository.insert(exportTask);
        this.taskService.setExportTaskStatus(exportTask.getId(), TaskStatus.running);
    }

    public ExportCtl.ExportTaskSpace getEstimateSpace(ExportTask exportTask) {
        if( exportTask.isCryosparc() ) {
            return new ExportCtl.ExportTaskSpace(0, 0);
        }
        int movieCount = 0;
        ExportSettings exportSettings = exportTask.getExportSettings();
        ExportSummary exportSummary = exportTask.getExportSummary();
        if( exportSummary == null ) {
            Task.Statistic movieStatistic = exportTask.getMovie_statistic();
            if( movieStatistic == null ) {
                return new ExportCtl.ExportTaskSpace(0, 0);
            }
            if( exportSettings.isExportRawMovie() ) {
                movieCount = movieCount + 1;
            }
            if( exportSettings.isExportMotionDw() ) {
                movieCount = movieCount + 1;
            }
            if( exportSettings.isExportMotion() ) {
                movieCount = movieCount + 1;
            }
            int ctfCount = exportSettings.isExportCTF() ? 1 : 0;

            long required = (movieCount * motionMovieSize + ctfCount * ctfMovieSize) * (movieStatistic.getTotal() - movieStatistic.getProcessed());
            long used = (movieCount * motionMovieSize + ctfCount * ctfMovieSize) * (movieStatistic.getProcessed());
            return new ExportCtl.ExportTaskSpace(used, required);
        } else {
            List<ExportSummary.Summary> summaries = Stream.of(
                    exportSummary.getDw(),
                    exportSummary.getMovie(),
                    exportSummary.getNoDw()
            ).filter(Objects::nonNull).toList();

            long total = summaries.stream().mapToLong(ExportSummary.Summary::getTotal).sum();
            long completed = summaries.stream().mapToLong(ExportSummary.Summary::getCompleted).sum();
            long totalCtf = Optional.ofNullable(exportSummary.getCtf()).map(ExportSummary.Summary::getTotal).orElse(0L);
            long completedCtf = Optional.ofNullable(exportSummary.getCtf()).map(ExportSummary.Summary::getCompleted).orElse(0L);
            long required = (total - completed) * motionMovieSize + (totalCtf - completedCtf) * ctfMovieSize;
            long used = completed * motionMovieSize + completedCtf * ctfMovieSize;
            return new ExportCtl.ExportTaskSpace(used, required);
        }
    }

    private void createDirExportTask(Task task) {
        ExportSettings exportSettings = task.getTaskSettings().getExportSettings();


        exportSettings.setExportMotionDw(exportSettings.isExportMotion());
        exportSettings.setExportGain(true);
        exportSettings.setExportRawMovie(true);
        if( exportSettings.getOutput_dir() == null ) {
            exportSettings.setOutput_dir(task.getOutput_dir());
        }
        if( exportSettings.getOutput_dir_tail() == null ) {
            exportSettings.setOutput_dir_tail(task.getOutput_dir_tail());
        }
        if( task.getIs_tomo() ) {
            exportSettings.setExportMdoc(true);
        }
        ExportTask exportTask = new ExportTask();
        exportTask.setTask_id(task.getId());
        exportTask.setId(getDefaultExportId(task));
        exportTask.setExportSettings(exportSettings);

//        exportTask.setOutputDir(task.getOutput_dir() + "/" + task.getOutput_dir_tail());
        exportTask.setDefault(true);
        exportTask.setName("Default");
        exportTask.setStatus(TaskStatus.running);
        exportTask.setCreated_at(new Date());
        checkOutput(exportTask);

        this.exportTaskRepository.insert(exportTask);
        this.taskService.setExportTaskStatus(exportTask.getId(), TaskStatus.running);
    }

    public static String getDefaultExportId(Task task) {
        return task.getId() + "_default_export";
    }

    public void create(ExportTask exportTask) {
        exportTask.setStatus(TaskStatus.running);
        checkOutput(exportTask);
        this.exportTaskRepository.insert(exportTask);
        Task task = this.taskRepository.findById(exportTask.getTask_id()).orElseThrow();
//        Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(task.getTaskSettings().getDataset_id()), task.getTaskSettings().getDataset_id()));
//        List<MovieDataset> movieDatasets = this.movieDataSetRepository.findByQuery(query);
//        for( MovieDataset movieDataset : movieDatasets ) {
//            createExportMovie(exportTask, movieDataset);
//        }
//        List<MDoc> mDocs = this.mDocRepository.findByQuery(query);
//        for( MDoc mDoc : mDocs ) {
//            if( mDoc.getName().contains("override") ) {
//                return;
//            }
//            createExportMdoc(exportTask, mDoc);
//        }

        this.taskService.setExportTaskStatus(exportTask.getId(), TaskStatus.running);
    }

    private void createExportMovie(ExportTask exportTask, MovieDataset movieDataset) {

        this.exportMovieService.createExportMovie(exportTask, movieDataset, null);
    }

    private void createExportMdoc(ExportTask exportTask, MDoc mdoc) {

        this.exportMovieService.createExportMdoc(exportTask, mdoc, null);
    }

    public void checkOutput(ExportTask exportTask) {
        if( exportTask.isCryosparc() ) {
            if( !exportTask.getOutputDir().startsWith(HOME_CRYOSPARC_DATA) && !exportTask.getOutputDir().startsWith(sessionService.getSessionUser().getUser().getDefault_dir()) ) {
                throw new RuntimeException("Cryosparc output must be in /home/cryosparc-data  or in default directory");
            }
        } else if( !exportTask.getOutputDir().startsWith(sessionService.getSessionUser().getUser().getDefault_dir()) ) {
            throw new RuntimeException("Output must be in default directory");
        }
    }

    public File getDefaultCryosparcOutputDir() {
        return new File(new File(HOME_CRYOSPARC_DATA), sessionService.getSessionUser().getUser().getDefault_dir().replace("/home/", ""));
    }

    public String getHOME_CRYOSPARC_DATA() {
        return HOME_CRYOSPARC_DATA;
    }
}
