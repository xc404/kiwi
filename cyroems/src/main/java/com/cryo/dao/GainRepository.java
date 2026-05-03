package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.Gain;
import com.cryo.model.GainConvertSoftware;
import com.cryo.model.GainConvertStatus;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface GainRepository extends BaseRepository<Gain, String>
{


    @Query("{ 'task_id' : ?0, 'gain_conversion_status' : { $in : ['unprocessed'] } }")
    List<Gain> getUnprocessedGains(String taskId);

    @Query("{ 'id' : ?0 }")
    @Update("{$set: { 'gain_conversion_status' : ?1, 'file_path' : ?2, 'gain_conversion_software' : ?3,  'updated_at' : ?4 }}")
    void updateStatus(String id, GainConvertStatus gain_conversion_status, String outputFile, GainConvertSoftware gain_conversion_software, Date updated_at);

    @Query("{ 'task_id' : ?0}")
    Optional<Gain> getGainByTask(String taskId);

//    @Query("{ 'task_id' : ?0, 'gain_conversion_output_file' : ?1 }")
//    Optional<Gain> findByFile(String taskId, String absolutePath);

}
