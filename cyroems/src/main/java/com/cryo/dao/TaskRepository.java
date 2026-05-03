package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.Task;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Date;
import java.util.List;

public interface TaskRepository extends BaseRepository<Task, String> {


    @Query("{ 'status' : { $in : ['running'] } }")
    List<Task> getRunningTasks();

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'last_file_detect_time' : ?1 } }")
    void updateLastDetectTime(String id, Date date);

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'movie_statistic' : ?1 } }")
    void setMovieStatistic(String id, Task.Statistic statistic);


    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'mdoc_statistic' : ?1 } }")
    void setMdocStatistic(String id, Task.Statistic statistic);

    @Query("{ 'taskSettings.dataset_id' : ?0 }")
    List<Task> findByDataSetId(String datasetId);

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'export_statistic' : ?1 } }")
    void setExportStatistic(String id, Task.Statistic statistic);

    @Query("{ 'created_at' : { $gte: ?0 } }")
    List<Task> findLast24HoursTasks(Date date);

    @Query("{ 'created_at' : { $gte: ?0, $lte: ?1 } }")
    List<Task> findTasksCreatedBetween(Date startTime, Date endTime);
}
