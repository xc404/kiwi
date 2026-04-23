package com.cryo.model.dataset;

import com.cryo.common.model.IdEntity;
import com.cryo.model.settings.TaskDataSetSetting;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Document("data")
@Data
public class TaskDataset extends IdEntity
{
    //    private String name;
    private String raw_path;
    private Boolean is_tomo;
    private Date created_at;
    private Date mtime;
    private String microscope;
    private String movie_path;
    private long mdoc_count;
    private long movies_count;
    private List<Gain> gain;
    private MovieSync movie_sync;
    private String owner;
    private String group;
    private List<String> collaborators;


    private String config_id;
    private TaskDataSetSetting taskDataSetSetting;

    @Data
    public static class Gain
    {
        private String path;
        private String usable_path;
        private Date created_at;
        private Date mtime;
    }

    @Data
    public static class MovieSync
    {
        private boolean done;
        private Date end_time;
    }

    public Gain getGain0() {
        if( this.gain == null || this.gain.isEmpty() ) {
            return null;
        }
        return this.gain.get(0);
    }

    public boolean getIs_tomo() {
        return Optional.ofNullable(this.is_tomo).orElse(false);
    }

    public boolean getMovie_sync_done() {
        return Optional.ofNullable(this.movie_sync).map(MovieSync::isDone).orElse(false);
    }
}
