package com.cryo.model.dataset;


import com.cryo.common.model.IdEntity;
import com.cryo.task.tilt.MDocMeta;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Document("mdoc")
@Data
public class MDoc extends IdEntity
{
    @Indexed
    private String belonging_data;
    private String path;
    private Date created_at;
    private Date mtime;
    private String name;
    private MDocMeta meta;
    private List<String> movie_data_ids;
    private boolean manualRebuild;

}
