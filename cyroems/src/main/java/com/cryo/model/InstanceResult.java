package com.cryo.model;

import com.cryo.common.model.DataEntity;
import com.cryo.task.movie.handler.ctf.EstimationResult;
import com.cryo.task.movie.handler.motion.MotionResult;
import com.cryo.task.movie.handler.vfm.VFMResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class InstanceResult extends DataEntity
{
    @Indexed
    private String task_data_id;
    @Indexed
    private String task_id;
    @Indexed
    private String instance_id;
    private String data_id;
    private String config_id;

    private String category;






}
