package com.cryo.model.export;

import com.cryo.model.Movie;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("exportMovie")
@Data
//@CompoundIndex(name = "instance_task_and_data_id_index", def = "{'task_id': 1, 'data_id': 1}", unique = true)
public class ExportMovie extends Movie
{


    public static enum CryospacStatus
    {
        Init,
        Processing,
        Complete
    }

    private String cryosparcPatchId;
    private CryospacStatus cryospacStatus;
    private Boolean isCryosparc;
}
