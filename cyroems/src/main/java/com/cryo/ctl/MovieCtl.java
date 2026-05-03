package com.cryo.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.cryo.dao.MovieRepository;
import com.cryo.dao.MovieResultRepository;
import com.cryo.dao.TaskRepository;
import com.cryo.model.Movie;
import com.cryo.model.MovieImage;
import com.cryo.model.MovieResult;
import com.cryo.model.Task;
import com.cryo.service.session.SessionService;
import com.cryo.task.engine.HandlerKey;
import com.cryo.task.engine.TaskStep;
import com.cryo.task.movie.TaskStatistic;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.result.R;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Controller
@Slf4j
public class MovieCtl
{

    private final TaskRepository taskRepository;
    private final MovieRepository movieRepository;
    private final TaskStatistic movieStatisticTask;
    private final MovieResultRepository movieResultRepository;
    private final SessionService sessionService;


    @GetMapping("/api/task/{taskId}/movies")
    @ResponseBody
    public Page<MovieOutput> movies(@PathVariable("taskId") String taskId,
                                    MovieQueryInput movieQueryInput,
                                    Pageable pageable) {
        Query query = Query.query(Criteria.where("task_id").is(taskId));
        query.fields().exclude("steps");
        Task task = this.taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not exist"));
        if( StringUtils.isNotBlank(movieQueryInput.status) ) {
            switch( movieQueryInput.status ) {
                case "unprocessed":
                    query.addCriteria(Criteria.where("current_step.key").ne(HandlerKey.FINISHED))
                            .addCriteria(Criteria.where("error.permanent").ne(true))
                            .addCriteria(Criteria.where("process_status.processing").ne(true))
                    ;
                    break;
                case "completed":
                case "processed":
                    query.addCriteria(Criteria.where("current_step.key").is(HandlerKey.FINISHED));
                    break;
                case "error":
                    query.addCriteria(Criteria.where("error.permanent").is(true));
                    break;
                case "processing":
                    query.addCriteria(Criteria.where("process_status.processing").is(true));
                case "all":
            }

        }


//        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(Query.query(Criteria.where("task_data_id")
//                .is(task.getTaskSettings().getDataset_id()).and("config_id").is(task.getDefault_config_id()))
//        );

        query.fields().exclude("steps");
        query.fields().exclude("cmds");
        Page<Movie> pageMovie = this.movieRepository.findByQuery(query, pageable);
        List<String> movieDataIds = pageMovie.stream().map(m -> m.getMovie_data_id()).toList();
        Query query1 = Query.query(Criteria.where("movie_data_id").in(movieDataIds).and("config_id").is(task.getDefault_config_id()));
        query1.fields().exclude("vfmResult.pointList");


        List<MovieResult> movieResults = this.movieResultRepository.findByQuery(query1);
        Map<String, MovieResult> movieResultMap = movieResults.stream()
                .collect(Collectors.toMap(MovieResult::getMovie_data_id, Function.identity(), MovieResult::pickNewer));

        Page<MovieOutput> all = pageMovie
                .map(m -> {
                    MovieResult movieResult = movieResultMap.get(m.getMovie_data_id());
                    return new MovieOutput(m, movieResult);
                });
        return all;
    }

    @PostMapping("/api/movie/{id}/restart")
    @ResponseBody
    @SaCheckLogin
    public void restore(@PathVariable("id") String id) {
        Movie movie = this.movieRepository.findById(id).orElseThrow();
        checkPermission(movie.getTask_id());
        this.movieRepository.restore(id);
    }


    @PostMapping("/api/movie/restart")
    @ResponseBody
    @SaCheckLogin
    public void restore(@RequestBody IdsInput input) {
        if( input.ids.isEmpty() ) {
            return;
        }
        Movie byId = this.movieRepository.findById(input.ids.get(0)).orElseThrow();
        Task t = checkPermission(byId.getTask_id());
        this.movieRepository.restore(input.ids);
        this.movieStatisticTask.statisticMovies(t);
    }


//    @GetMapping(value = "/api/movie/{id}-{imageType}.png", produces = MediaType.IMAGE_PNG_VALUE)
//    public @ResponseBody byte[] getMoviePng(@PathVariable String id, @PathVariable("imageType") MovieImage.Type imageType) {

    /// /
    /// /        if( imageType == MovieImage.Type.vfm){
    /// /            MovieResult movieResult = this.movieResultRepository.findById(id).orElse(null);
    /// /        }
    /// /
//        MovieImage image = this.movieService.get(id, imageType);
//        try {
//            return FileUtils.readFileToByteArray(new File(image.getPath()));
//        } catch( IOException e ) {
//            throw new RuntimeException(e);
//        }
//    }
    @GetMapping(value = "/api/movie/result/{id}-{imageType}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public @ResponseBody byte[] getMovieResultPng(@PathVariable String id, @PathVariable("imageType") MovieImage.Type imageType) {
//
        MovieResult movieResult = this.movieResultRepository.findById(id).orElseThrow();
        if( imageType == MovieImage.Type.vfm ) {
            try {
                return FileUtils.readFileToByteArray(new File(movieResult.getVfmResult().getPngFile()));
            } catch( IOException e ) {
                throw new RuntimeException(e);
            }
        }
        MovieImage image = movieResult.getImages().get(imageType);
        try {
            return FileUtils.readFileToByteArray(new File(image.getPath()));
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }


    @GetMapping("/api/task/{taskId}/movies/defocus")
    @ResponseBody
    public Page<MovieOutput> getMovieResultsByDefocusRangeDb(@PathVariable String taskId,
                                                              @RequestParam double defocusMin,
                                                              @RequestParam double defocusMax,
                                                              Pageable pageable) {
        log.info("[defocus filter] querying taskId={}, range=[{}, {}]", taskId, defocusMin, defocusMax);

        Query query = Query.query(Criteria.where("task_id").is(taskId));
        query.fields().exclude("steps");
        Task task = this.taskRepository.findById(taskId).orElseThrow(() -> new RuntimeException("Task not exist"));
        query.fields().exclude("steps");
        query.fields().exclude("cmds");
        List<Movie> movies = this.movieRepository.findByQuery(query);
        Map<String, Movie> movieMap = movies.stream().collect(Collectors.toMap(m -> m.getMovie_data_id(), m -> m));
        List<String> movieDataIds = movies.stream().map(m -> m.getMovie_data_id()).toList();

        // 用 $expr + $avg 在 MongoDB 侧计算 defocus_1 和 defocus_2 的均值并过滤
        Document avgExpr = new Document("$avg", List.of("$ctfEstimation.defocus_1", "$ctfEstimation.defocus_2"));
        Document exprCondition = new Document("$and", List.of(
                new Document("$gte", List.of(avgExpr, defocusMin)),
                new Document("$lte", List.of(avgExpr, defocusMax))
        ));
        Document filterDoc = new Document("movie_data_id", new Document("$in", movieDataIds))
                .append("config_id", task.getDefault_config_id())
                .append("ctfEstimation.defocus_1", new Document("$exists", true))
                .append("ctfEstimation.defocus_2", new Document("$exists", true))
                .append("$expr", exprCondition);

        Document projectionDoc = new Document("vfmResult.pointList", 0);

        BasicQuery query1 = new BasicQuery(filterDoc, projectionDoc);
        Page<MovieResult> page = movieResultRepository.findByQuery(query1, pageable);
        log.info("[defocus filter] page {}/{}, total={}", pageable.getPageNumber(), page.getTotalPages(), page.getTotalElements());

        Map<String, MovieResult> movieResultMap = page.stream().collect(Collectors.toMap(m -> m.getMovie_data_id(), m -> m));

        return page.map(r -> new MovieOutput(movieMap.get(r.getMovie_data_id()), r));
    }

    @GetMapping(value = "/api/movie/{id}/result")
    public @ResponseBody MovieOutput getResult(@PathVariable String id) {
//
        Movie movie = this.movieRepository.findById(id).orElseThrow();
        Task task = this.taskRepository.findById(movie.getTask_id()).orElseThrow();
        Optional<MovieResult> movieResult = this.movieResultRepository.findByDataId(movie.getData_id(), task.getDefault_config_id());
        return new MovieOutput(movie, movieResult.orElse(null));
    }

    private Task checkPermission(String taskId) {
        Optional<Task> task = this.taskRepository.findById(taskId);
        if( task.isPresent() ) {
            Task t = task.get();
            String owner = t.getOwner();
            if( !sessionService.isAdmin() && !owner.equals(sessionService.getSessionUser().getUser().getId()) ) {
                throw new RuntimeException("No permission");
            }
            this.movieStatisticTask.statisticMovies(t);
        } else {
            throw new RuntimeException("No task found");
        }
        return task.get();
    }

    @Data
    public static class IdsInput
    {
        private List<String> ids;
    }

    @Data
    public static class MovieQueryInput
    {
        private String status;
    }

    public static class MovieOutput
    {

        @JsonIgnore
        private final Movie movie;
        @JsonUnwrapped
        private final MovieResult result;

        public MovieOutput(Movie movie, MovieResult movieResult) {
            this.movie = movie;
            this.result = movieResult;
        }

        public String getStatus() {

            if( this.movie.getCurrent_step().getKey() == HandlerKey.FINISHED ) {
                return "processed";
            }
            if( Optional.ofNullable(this.movie.getError()).map(e -> e.getPermanent()).orElse(false) ) {
                return "error";
            }
            if( this.movie.getProcess_status().isProcessing() ) {
                return "processing";
            }
            return "unprocessed";

        }

        public String getStatusMessage() {
            R<Void> status = movie.getStatus();
            return status != null ? status.getMsg() : "Waiting for processing";
        }

        public TaskStep getCurrentStep() {
            return this.movie.getCurrent_step();
        }

        public String getIndex() {
            String fileName = movie.getFile_name();
            String[] split = fileName.split("_");
            for( int i = split.length - 1; i >= 0; i-- ) {
                String s = split[i];
                if( NumberUtils.isDigits(s.substring(0, 1)) ) {
                    return s;
                }
            }
            return fileName;
        }

        public String getId() {
            return this.movie.getId();
        }


        public String getResultId() {
            return Optional.ofNullable(this.result).map(r -> r.getId()).orElse(null);
        }
    }

}
