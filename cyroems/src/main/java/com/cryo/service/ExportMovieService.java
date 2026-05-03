package com.cryo.service;

import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.export.ExportMDocInstanceRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.model.dataset.MDoc;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.export.ExportMDocInstance;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportTask;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.export.ExportTaskVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.result.R;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportMovieService
{
    private final ExportMovieRepository exportMovieRepository;
    private final ExportMDocInstanceRepository mDocInstanceRepository;


    public void createExportMovie(ExportTask exportTask, MovieDataset movieDataset, @Nullable HandlerKey handlerKey) {
        if( handlerKey == null ) {
            handlerKey = HandlerKey.INIT;
        }
        Optional<ExportMovie> byDataId = this.exportMovieRepository.findByDataId(exportTask.getId(), movieDataset.getId());

        if( byDataId.isPresent() ) {
            return;
        }
        String file = movieDataset.getPath();
        ExportMovie exportMovie = new ExportMovie();
        exportMovie.setTask_id(exportTask.getId());
//        exportMovie.setTask_name(task.getTask_name());
        exportMovie.setFile_path(file);
        exportMovie.setStatus(R.success());
        exportMovie.setFile_name(FileNameUtil.getPrefix(file));
        exportMovie.setData_id(movieDataset.getId());
        exportMovie.setMovie_data_id(movieDataset.getId());
        exportMovie.setFile_create_at(movieDataset.getMtime());
        exportMovie.setCurrent_step(TaskStep.of(handlerKey));
        exportMovie.setIsCryosparc(exportTask.isCryosparc());
        if( exportTask.isCryosparc() ) {
            exportMovie.setCryospacStatus(ExportMovie.CryospacStatus.Init);
        }
        this.exportMovieRepository.insert(exportMovie);
    }

    public void createExportMovie(ExportTaskVo exportTaskVo, ExportMovie toFold) {
        ExportTask exportTask = exportTaskVo.getExportTask();
        String defaultOutputDir = exportTaskVo.getTask().getDefaultOutputDir();
        Optional<ExportMovie> byDataId;
        try {
            byDataId = this.exportMovieRepository.findByDataId(exportTask.getId(), toFold.getData_id());
        } catch( IncorrectResultSizeDataAccessException e ) {
            log.error(e.getMessage(), e);
            this.exportMovieRepository.deleteByDataId(exportTask.getId(), toFold.getData_id());
            byDataId = Optional.empty();
        }
        if( byDataId.isPresent() ) {
            return;
        }

//        String file = movieDataset.getPath();
        String fileName = FileNameUtil.getName(toFold.getFile_path());
        File file = new File(defaultOutputDir, fileName);

        ExportMovie exportMovie = new ExportMovie();
        exportMovie.setTask_id(exportTask.getId());
//        exportMovie.setTask_name(task.getTask_name());
        exportMovie.setFile_path(file.getAbsolutePath());
        exportMovie.setStatus(R.success());
        exportMovie.setFile_name(FileNameUtil.getPrefix(file));
        exportMovie.setData_id(toFold.getData_id());
        exportMovie.setMovie_data_id(toFold.getData_id());
        exportMovie.setFile_create_at(toFold.getFile_create_at());
        exportMovie.setCurrent_step(TaskStep.of(HandlerKey.INIT));
        exportMovie.setIsCryosparc(exportTask.isCryosparc());
        if( exportTask.isCryosparc() ) {
            exportMovie.setCryospacStatus(ExportMovie.CryospacStatus.Init);
        }
        this.exportMovieRepository.insert(exportMovie);
    }

    public void createExportMdoc(ExportTask exportTask, MDoc movieDataset, @Nullable HandlerKey handlerKey) {
        if( handlerKey == null ) {
            handlerKey = HandlerKey.INIT;
        }
        Optional<ExportMDocInstance> byDataId = this.mDocInstanceRepository.findByDataId(exportTask.getId(), movieDataset.getId());
        if( byDataId.isPresent() ) {
            return;
        }
        String file = movieDataset.getPath();
        ExportMDocInstance movie = new ExportMDocInstance();
        movie.setTask_id(exportTask.getId());
        movie.setTask_name(exportTask.getName());
        movie.setFile_path(file);
        movie.setStatus(R.success());
        movie.setName(FileNameUtil.getPrefix(file));
        movie.setData_id(movieDataset.getId());
        movie.setFile_create_at(movieDataset.getMtime());
        movie.setCurrent_step(TaskStep.of(handlerKey));
        this.mDocInstanceRepository.insert(movie);
    }
}
