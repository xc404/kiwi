package com.cryo.dao.export;

import com.cryo.dao.InstanceRepository;
import com.cryo.model.export.ExportMDocInstance;
import com.cryo.model.tilt.MDocInstance;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

public interface ExportMDocInstanceRepository extends InstanceRepository<ExportMDocInstance>
{

    @Query("{ 'id' : { $in: ?0 } }")
    @Update("{ $set: { 'current_step' : 'MDocInit', 'forceReset': true  }, $unset: { 'error': 1, cmds: 1, steps: 1} }")
    void restore(List<String> ids);
}
