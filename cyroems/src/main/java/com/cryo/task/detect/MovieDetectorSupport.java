package com.cryo.task.detect;


import cn.hutool.core.io.file.FileNameUtil;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.dao.dataset.MovieDataSetRepository;
import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.dao.export.ExportTaskRepository;
import com.cryo.model.Movie;
import com.cryo.model.Task;
import com.cryo.model.dataset.MovieDataset;
import com.cryo.model.export.ExportMovie;
import com.cryo.model.export.ExportTask;
import com.cryo.service.ExportMovieService;
import com.cryo.service.ExportTaskService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.export.ExportTaskVo;
import com.cryo.task.movie.TaskStatistic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.result.R;
import org.apache.commons.lang3.time.DateUtils;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.cryo.service.ExportTaskService.getDefaultExportId;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieDetectorSupport
{
    private final MovieDataSetRepository movieDatasetRepository;
    private final MovieRepository movieRepository;
    private final ExportMovieRepository exportMovieRepository;
    private final TaskRepository taskRepository;
    private final ExportTaskRepository exportTaskRepository;
    private final TaskStatistic movieStatisticTask;
    private final ExportMovieService exportMovieService;
    private final ExportTaskService exportTaskService;

    public void detect(Task task) {
        Date lastDetectTime = task.getLast_detect_time();
        if( lastDetectTime == null ) {
            lastDetectTime = new Date(0);
        } else {
            lastDetectTime = DateUtils.addMinutes(lastDetectTime, -5);
        }
        Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(task.getTaskSettings().getDataset_id()), task.getTaskSettings().getDataset_id()));
        List<MovieDataset> movieDatasets = this.movieDatasetRepository.findByQuery(query.addCriteria(Criteria.where("created_at").gt(lastDetectTime)));
        if( movieDatasets.isEmpty() ) {
            return;
        }
        movieDatasets.sort(Comparator.comparing(MovieDataset::getCreated_at, Comparator.nullsFirst(Comparator.naturalOrder())));
        movieDatasets.forEach(movieDataset -> {
            createMovie(task, movieDataset);
        });
        movieStatisticTask.statisticTask(task);
    }


    public void detectExport(ExportTaskVo exportTaskVo) {


        synchronized( exportTaskVo.getExportTask().getId().intern() ) {
//        Task task = exportTaskVo.getTask();
            ExportTask exportTask = exportTaskVo.getExportTask();
            Date lastDetectTime = exportTask.getLast_detect_time();
            if( lastDetectTime == null ) {
                lastDetectTime = new Date(0);
            } else {
                lastDetectTime = DateUtils.addMinutes(lastDetectTime, -5);
            }

            if( exportTask.isCryosparc() ) {
                Query query = Query.query(Criteria.where("task_id").in(getDefaultExportId(exportTaskVo.getTask())).and("current_step.key").is(HandlerKey.FINISHED));
                List<ExportMovie> exportMovies = this.exportMovieRepository.findByQuery(query.addCriteria(Criteria.where("created_at").gt(lastDetectTime)));
                if( exportMovies.isEmpty() ) {
                    return;
                }
                exportMovies.sort(Comparator.comparing(ExportMovie::getCreated_at, Comparator.nullsFirst(Comparator.naturalOrder())));
                exportMovies.forEach(exportMovie -> {
                    this.exportMovieService.createExportMovie(exportTaskVo, exportMovie);
                });
            } else {
                Query query = Query.query(Criteria.where("belonging_data").in(new ObjectId(exportTaskVo.getTask().getTaskSettings().getDataset_id()),
                        exportTaskVo.getTask().getTaskSettings().getDataset_id()));
                List<MovieDataset> movieDatasets = this.movieDatasetRepository.findByQuery(query.addCriteria(Criteria.where("created_at").gt(lastDetectTime)));
                if( movieDatasets.isEmpty() ) {
                    return;
                }
                movieDatasets.sort(Comparator.comparing(MovieDataset::getCreated_at, Comparator.nullsFirst(Comparator.naturalOrder())));
                movieDatasets.forEach(movieDataset -> {
                    this.exportMovieService.createExportMovie(exportTask, movieDataset, null);
                });
            }
        }


        movieStatisticTask.statisticMovieExport(exportTaskVo);
    }


    private void createMovie(Task task, MovieDataset movieDataset) {
        Optional<Movie> byDataId = this.movieRepository.findByDataId(task.getId(), movieDataset.getId());
        if( byDataId.isPresent() ) {
            return;
        }
        String file = movieDataset.getPath();
        Movie movie = new Movie();
        movie.setTask_id(task.getId());
        movie.setTask_name(task.getTask_name());
        movie.setFile_path(file);
        movie.setStatus(R.success());
        movie.setFile_name(FileNameUtil.getPrefix(file));
        movie.setData_id(movieDataset.getId());
        movie.setMovie_data_id(movieDataset.getId());
        movie.setFile_create_at(movieDataset.getMtime());
        movie.setCurrent_step(TaskStep.of(HandlerKey.INIT));
        this.movieRepository.insert(movie);
    }

    private void createExportMovie(Task task, ExportTask exportTask, MovieDataset movieDataset) {
        this.exportMovieService.createExportMovie(exportTask, movieDataset, null);
    }


}
