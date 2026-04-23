package com.cryo.model;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Objects;
import java.util.Optional;

@Data
@Document("movie")
//@CompoundIndex(name = "instance_task_and_data_id_index", def = "{'task_id': 1, 'data_id': 1}", unique = true)
public class Movie extends Instance
{

    private Integer index;
    private String movie_data_id;
    private String file_name;

    @Override
    public boolean equals(Object o) {
        if( !(o instanceof Movie movie) ) {
            return false;
        }
        if( !super.equals(o) ) {
            return false;
        }
        return Objects.equals(getId(), movie.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String getName() {
        return Optional.ofNullable(super.getName()).orElse(file_name);
    }

    @Override
    public String getData_id() {
        return Optional.ofNullable(super.getData_id()).orElse(this.movie_data_id);
    }

    @Override
    public void setData_id(String data_id) {
        super.setData_id(data_id);
        this.setMovie_data_id(data_id);
    }

    @Override
    public void setName(String name) {
        super.setName(name);
        setFile_name(name);
    }
}
