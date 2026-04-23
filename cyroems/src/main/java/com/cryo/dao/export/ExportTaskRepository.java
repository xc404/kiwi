package com.cryo.dao.export;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.Task;
import com.cryo.model.export.ExportSummary;
import com.cryo.model.export.ExportTask;
import com.cryo.task.export.cryosparc.CryosparcCompleteStatus;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Date;
import java.util.List;

public interface ExportTaskRepository extends BaseRepository<ExportTask, String> {


    @Query("{ 'status' : { $in : ['running'] } }")
    List<ExportTask> getRunningTasks();

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'last_file_detect_time' : ?1 } }")
    void updateLastDetectTime(String id, Date date);

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'movie_statistic' : ?1 } }")
    void setMovieStatistic(String id, Task.Statistic statistic);




    @Query("{ 'task_id' : ?0 }")
    List<ExportTask> findByTaskId(String taskId);

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'cryosparcCompleteStatus' : ?1 } }")
    void setCryosparcCompleteStatus(String id, CryosparcCompleteStatus cryosparcCompleteStatus);

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'exportSummary' : ?1 } }")
    void setSummary(String id, ExportSummary exportSummary);

    @Query("{ 'id' : ?0 }")
    @Update("{ $set: { 'mdoc_statistic' : ?1 } }")
    void setMdocStatistic(String id, Task.Statistic statistic);
}
