package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.Instance;
import com.cryo.task.engine.HandlerKey;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;
import java.util.Optional;

public interface InstanceRepository<T extends Instance> extends BaseRepository<T, String>
{



    @Query("{ 'task_id' : ?0,  'file_path': ?1}")
    Optional<T> findByFile(String taskId, String file);

    @Query("{ 'task_id' : ?0,  'data_id': ?1}")
    Optional<T> findByDataId(String taskId, String datasetId);

    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'current_step' : ?1 } }")
    void setStep(String id, HandlerKey movieStep);


    @Query("{ 'id' : ?0}")
    @Update("{ $set: { 'index' : ?1 } }")
    void setIndex(String id, int index);


//    @Query("{ 'id' : ?0}")
//    @Update("{ $set: { 'current_step' : 'INIT'  }, $unset: { 'error': 1, cmds: 1, steps: 1} }")
//    void restore(String id);

    //重置
//    void restore(List<String> ids);

    //继续当前步骤
    @Query("{ 'task_id' : ?0, 'error.permanent': true }")
    @Update("{ $unset: { 'error': 1} }")
    void continueByTaskId(String id);


    public static Criteria unprocessed() {
        return Criteria.where("current_step.key").ne("FINISHED")
                .and("error.permanent").ne(true).
                and("waiting").ne(true)
                .and("process_status.processing").ne(true);
    }

    public static Criteria waiting() {
        return Criteria.where("current_step.key").ne("FINISHED")
                .and("error.permanent").ne(true)
                .and("waiting").is(true);
    }

    static CriteriaDefinition processing() {
        return Criteria.
        where("process_status.processing").is(true);
    }


}
