package com.cryo.model.dataset;

import com.cryo.common.model.IdEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document("movies")
@EqualsAndHashCode(callSuper = true)
@Data
public class MovieDataset extends IdEntity
{

    @Indexed
    private String belonging_data;
    private String path;
    private Date created_at;
    private Date mtime;
    private String name;
}
