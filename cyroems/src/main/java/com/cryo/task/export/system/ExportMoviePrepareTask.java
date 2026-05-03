//package com.cryo.task.export.system;
//
//
//import com.cryo.dao.export.ExportMovieRepository;
//import com.cryo.dao.MovieRepository;
//import com.cryo.model.Movie;
//import com.cryo.model.Task;
//import com.cryo.task.engine.HandlerKey;
//import com.cryo.task.engine.TaskStep;
//import com.cryo.model.export.ExportMovie;
//import org.apache.commons.lang3.time.DateUtils;
//import org.springframework.context.ApplicationContext;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.util.Date;
//import java.util.List;
//import java.util.Optional;
//
//@Service
//public class ExportMoviePrepareTask
//{
//    private final ExportMovieRepository exportMovieRepository;
//    private final MovieRepository movieRepository;
//    private Date lastCheckTime;
//
//    public ExportMoviePrepareTask(ApplicationContext applicationContext) {
//        this.exportMovieRepository = applicationContext.getBean(ExportMovieRepository.class);
//        this.movieRepository = applicationContext.getBean(MovieRepository.class);
//    }
//
//    public void prepare(Task task) {
//        if( task.equalsDefault() ) {
//            List<Movie> completedMovies = this.getCompletedMovies(task);
//            Date date = Date.from(Instant.now());
//            completedMovies.forEach(movie -> {
//                Optional<ExportMovie> byDataId =
//                        this.exportMovieRepository.findByDataId(task.getId(), movie.getData_id());
//                if( byDataId.isPresent() ) {
//                    ExportMovie exportMovie = byDataId.get();
//                    if( exportMovie.getCurrent_step().getKey() == HandlerKey.INIT ) {
//                        exportMovie.setCurrent_step(TaskStep.of(HandlerKey.ExportReady));
//                        this.exportMovieRepository.save(exportMovie);
//                    }
//                }
//            });
//            this.lastCheckTime = date;
//        } else {
//            List<ExportMovie> unprocessedMovies = this.exportMovieRepository.findByQuery(
//                    Query.query(Criteria.where("task_id").is(task.getId()))
//                            .addCriteria(Criteria.where("current_step.key").is(HandlerKey.INIT)));
//            unprocessedMovies.forEach(movie -> {
//                movie.setCurrent_step(TaskStep.of(HandlerKey.ExportReady));
//                this.exportMovieRepository.save(movie);
//            });
//        }
//    }
//
//    private List<Movie> getCompletedMovies(Task task) {
//        Query query = Query.query(Criteria.where("task_id").is(task.getId()))
//                .addCriteria(Criteria.where("current_step.key").is(HandlerKey.FINISHED));
////                .addCriteria(Criteria.where("updated_at"));
//        if( lastCheckTime != null ) {
//            query.addCriteria(Criteria.where("updated_at")
//                    .gt(DateUtils.addMinutes(lastCheckTime, -30)));
//        }
//        return this.movieRepository.findByQuery(query);
//    }
//
////    private List<ExportMovie> getUnprocessedMovies(String taskId) {
////        Query query = Query.query(Criteria.where("task_id").is(taskId));
////        query.with(Sort.by(Sort.Order.asc("file_create_at")));
////        query.addCriteria(Criteria.where("current_step.key").is(HandlerKey.INIT)
////                .and("error.permanent").ne(true)
////                .and("process_status.processing").ne(true)
////        );
////        query.limit(2 * 10);
////        List<ExportMovie> exportMovies = this.exportMovieRepository.findByQuery(query);
////        if( exportMovies.isEmpty() ) {
////            return exportMovies;
////        }
////        return exportMovies;
////    }
//
//    public void reset() {
//        this.lastCheckTime = null;
//    }
//}
