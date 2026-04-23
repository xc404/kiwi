package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.Movie;
import com.cryo.task.engine.HandlerKey;
import com.cryo.model.MovieImage;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface MovieRepository extends InstanceRepository<Movie>
{


//    @Query(value = "{ 'task_id' : ?0, 'current_step' : { $ne : FINISHED }, 'error.permanent': { $ne : true }, 'process_status.processing': {$ne: true}}", sort = "{ 'file_create_at' : 1 }")
//    List<Movie> getUnprocessedMovies(String taskId, Pageable pageable);

    @Query("{ 'task_id' : ?0,  'file_path': ?1}")
    Optional<Movie> findByFile(String taskId, String file);

    @Query("{ 'task_id' : ?0,  'movie_data_id': ?1}")
    Optional<Movie> findByDataId(String taskId, String datasetId);

    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'images.?1' : ?2 } }")
    void addImage(String id, MovieImage.Type type, MovieImage movieImage);

    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'current_step' : ?1 } }")
    void setStep(String id, HandlerKey movieStep);


    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'index' : ?1 } }")
    void setIndex(String id, int index);


    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'current_step' : 'INIT'  }, $unset: { 'error': 1, cmds: 1, steps: 1} }")
    void restore(String id);

    //重置
    @Query("{ 'id' : { $in: ?0 } }")
    @Update("{ $set: { 'current_step' : 'INIT', 'forceReset': true  }, $unset: { 'error': 1, cmds: 1, steps: 1} }")
    void restore(List<String> ids);

    //继续当前步骤
    @Query("{ 'task_id' : ?0, 'error.permanent': true }")
    @Update("{ $unset: { 'error': 1} }")
    void continueByTaskId(String id);



}
