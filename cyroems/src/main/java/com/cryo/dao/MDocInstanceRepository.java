package com.cryo.dao;

import com.cryo.model.tilt.MDocInstance;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

public interface MDocInstanceRepository extends InstanceRepository<MDocInstance>
{

    @Query("{ 'id' : { $in: ?0 } }")
    @Update("{ $set: { 'current_step' : 'MDocInit', 'forceReset': true  }, $unset: { 'error': 1, cmds: 1, steps: 1} }")
    void restore(List<String> ids);
}
