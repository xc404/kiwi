package com.cryo.dao.export;

import com.cryo.dao.InstanceRepository;
import com.cryo.model.export.ExportMovie;
import com.cryo.task.engine.HandlerKey;
import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface ExportMovieRepository extends InstanceRepository<ExportMovie>
{


//    @Query(value = "{ 'task_id' : ?0, 'current_step' : { $ne : FINISHED }, 'error.permanent': { $ne : true }, 'process_status.processing': {$ne: true}}", sort = "{ 'file_create_at' : 1 }")
//    List<Movie> getUnprocessedMovies(String taskId, Pageable pageable);

    @Query("{ 'task_id' : ?0,  'file_path': ?1}")
    Optional<ExportMovie> findByFile(String taskId, String file);

    @Query("{ 'task_id' : ?0,  'movie_data_id': ?1}")
    Optional<ExportMovie> findByDataId(String taskId, String datasetId);


    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'current_step' : ?1 } }")
    void setStep(String id, HandlerKey movieStep);


    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'index' : ?1 } }")
    void setIndex(String id, int index);


    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'current_step' : 'INIT', 'forceReset': true, 'cryospacStatus': 'Init'   }, $unset: { 'error': 1, cmds: 1, steps: 1} }")
    void restore(String id);

    //重置
    @Query("{ 'id' : { $in: ?0 } }")
    @Update("{ $set: { 'current_step' : 'INIT', 'forceReset': true, 'cryospacStatus': 'Init'  }, $unset: { 'error': 1, cmds: 1, steps: 1} }")
    void restore(List<String> ids);

    //继续当前步骤
    @Query("{ 'task_id' : ?0, 'error.permanent': true }")
    @Update("{ $unset: { 'error': 1} }")
    void continueByTaskId(String id);

    @DeleteQuery("{ 'task_id' : ?0,  'movie_data_id': ?1}")
    void deleteByDataId(String taskId, String dataId);
}
