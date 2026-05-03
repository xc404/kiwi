package com.cryo.service;

import com.cryo.dao.export.ExportMovieRepository;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.model.Movie;
import com.cryo.task.engine.HandlerKey;
import com.cryo.model.MovieImage;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.movie.MovieContext;
import com.cryo.task.movie.handler.motion.MotionResult;
import com.cryo.task.support.ExportSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService
{


    private final MovieRepository movieRepository;
    private final ExportMovieRepository exportMovieRepository;
    private final SoftwareService softwareService;
    private final FilePathService filePathService;
    private final TaskRepository taskRepository;
    private final MovieResultRepository movieResultRepository;
    private final ExportSupport exportSupport;



    public MovieImage get(String id, MovieImage.Type type) {
        Movie movie = this.movieRepository.findById(id).orElseThrow(() -> new RuntimeException("movie not exist"));
        Task task = this.taskRepository.findById(movie.getTask_id()).orElseThrow(() -> new RuntimeException("task not exist"));
        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(Query.query(Criteria.where("movie_data_id")
                .is(movie.getData_id()).and("config_id").is(task.getDefault_config_id())));
        if(movieResults.isEmpty()){
            return null;
        }
        if(type == MovieImage.Type.vfm){
            return new MovieImage(MovieImage.Type.vfm, movieResults.get(0).getVfmResult().getPngFile());
        }
        return movieResults.get(0).getImages().get(type);
    }

    public void createImage(MovieContext movieContext, MovieImage.Type type) {
        MovieImage image;
        switch( type ) {
//            case motion_mrc -> {
//                image = createMotionMrc(movieContext);
//            }
//            case ctf -> {
//                image = createCtf(movieContext);
//            }
            case patch_log -> {
                image = createPatchLog(movieContext);
            }
            default -> {
                throw new UnsupportedOperationException("unsupported type");
            }
        }

        movieContext.getResult().getImages().put(type, image);
    }

    private MovieImage createPatchLog(MovieContext movieContext) {
//        Task task = movieContext.getTask();
        Movie movie = movieContext.getInstance();
        MovieResult result = movieContext.getResult();
        MotionResult motionCorrection = result.getMotion();
        if( motionCorrection == null ) {
            throw new RuntimeException("motion correction not exist");
        }
        String patchLogFile = motionCorrection.getLocal_motion().getPatch_log_file();
        File file = new File(patchLogFile);
        if( !file.exists() ) {
            throw new RuntimeException("motion patch log file not exist");
        }
        String outputFile = new File(this.filePathService.getImageWorkDir(movieContext), movie.getFile_name() + "_local_shifts.png").getAbsolutePath();
        this.softwareService.patch_log_png(patchLogFile, outputFile).startAndWait();
        exportSupport.toSelf(new File(outputFile));
        return new MovieImage(MovieImage.Type.patch_log, outputFile);

    }

    private MovieImage createCtf(MovieContext movieContext) {
        Movie movie = movieContext.getMovie();
        MovieResult result = movieContext.getResult();
        String inputFile = result.getCtfEstimation().getOutputFile();
        File file = new File(inputFile);
        if( !file.exists() ) {
            throw new RuntimeException("ctf estimation file not exist");
        }
        String outputFile = new File(this.filePathService.getImageWorkDir(movieContext), movie.getFile_name() + "_freq.png").getAbsolutePath();
        this.softwareService.ctf_png(inputFile, outputFile).startAndWait();
        return new MovieImage(MovieImage.Type.ctf, outputFile);
    }

    private MovieImage createMotionMrc(MovieContext movieContext) {
        Movie movie = movieContext.getMovie();
        MovieResult result = movieContext.getResult();
        MotionResult motionCorrection = result.getMotion();
        if( motionCorrection == null ) {
            throw new RuntimeException("motion correction not exist");
        }
        String inputFile = motionCorrection.getDw().getPath();
        File file = new File(inputFile);
        if( !file.exists() ) {
            throw new RuntimeException("motion correction file not exist");
        }
        String outputFile = new File(this.filePathService.getImageWorkDir(movieContext), movie.getFile_name() + "_DW_thumb_@1024.png").getAbsolutePath();
        this.softwareService.mrc_png(inputFile, outputFile).startAndWait();
        return new MovieImage(MovieImage.Type.motion_mrc, outputFile);
    }

    public void setStep(String id, HandlerKey movieStep) {
        this.movieRepository.findById(id).orElseThrow(() -> new RuntimeException("movie not exist"));
        this.movieRepository.setStep(id, movieStep);
    }


    public void sortMovie(String taskId) {
        Query query = Query.query(Criteria.where("task_id").is(taskId).and("index").exists(false));
        long indexNotExist = this.movieRepository.countByQuery(query);
        if( indexNotExist > 0 ) {
            Query sortQuery = Query.query(Criteria.where("task_id").is(taskId)).with(Sort.by(Sort.Order.asc("file_create_at")));
            List<Movie> movies = this.movieRepository.findByQuery(sortQuery);
            for( int i = 0; i < movies.size(); i++ ) {
                Movie movie = movies.get(i);
                if( movie.getIndex() == null || movie.getIndex() != 0 ) {
                    this.movieRepository.setIndex(movie.getId(), i);
                }
            }
        }
    }

//    public void sortExportMovie(String taskId) {
//        Query query = Query.query(Criteria.where("task_id").is(taskId).and("index").exists(false));
//        long indexNotExist = this.exportMovieRepository.countByQuery(query);
//        if( indexNotExist > 0 ) {
//            Query sortQuery = Query.query(Criteria.where("task_id").is(taskId)).with(Sort.by(Sort.Order.asc("file_create_at")));
//            List<ExportMovie> movies = this.exportMovieRepository.findByQuery(sortQuery);
//            for( int i = 0; i < movies.size(); i++ ) {
//                ExportMovie movie = movies.get(i);
//                if( movie.getIndex() == null || movie.getIndex() != 0 ) {
//                    this.exportMovieRepository.setIndex(movie.getId(), i);
//                }
//            }
//        }
//    }
}
