package com.cryo.model.export;

import com.cryo.model.MDocResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@Document
//@CompoundIndex(name = "result_data_and_config_index", def = "{'data_id': 1, 'config_id': 1}", unique = true)
public class ExporMDocResult extends MDocResult
{
}
