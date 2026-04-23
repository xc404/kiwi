package com.cryo.model.export;

import com.cryo.model.tilt.MDocInstance;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("exportMdocInstance")
@CompoundIndex(name = "instance_task_and_data_id_index", def = "{'task_id': 1, 'data_id': 1}", unique = true)
public class ExportMDocInstance extends MDocInstance
{
}
