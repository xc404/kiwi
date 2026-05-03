package com.cryo.model.tilt;

import com.cryo.model.Instance;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("mDocInstance")
//@CompoundIndex(name = "instance_task_and_data_id_index", def = "{'task_id': 1, 'data_id': 1}", unique = true)
public class MDocInstance extends Instance
{


}
