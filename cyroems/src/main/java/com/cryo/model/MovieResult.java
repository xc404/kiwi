package com.cryo.model;

import com.cryo.task.export.cryosparc.CryosparcResult;
import com.cryo.task.movie.handler.ctf.EstimationResult;
import com.cryo.task.movie.handler.motion.MotionResult;
import com.cryo.task.movie.handler.vfm.VFMResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
@Document
//@CompoundIndex(name = "movie_movie_data_id_index", def = "{'movie_data_id': 1, 'config_id': 1}", unique = true)
public class MovieResult extends InstanceResult
{
    private String movie_data_id;

    private Map<MovieImage.Type, MovieImage> images;
    private MrcMetadata mrcMetadata;
    private MotionResult motion;
    private EstimationResult ctfEstimation;
    private VFMResult vfmResult;
    private String type;
    private CryosparcResult cryosparcResult;

    public Map<MovieImage.Type, MovieImage> getImages() {
        if( this.images == null ) {
            this.images = new HashMap<>();
        }
        return this.images;
    }

    public void addImage(MovieImage movieImage) {
        getImages().put(movieImage.getType(), movieImage);
    }

    @Override
    public String getData_id() {
        return Optional.ofNullable(super.getData_id()).orElse(getMovie_data_id());
    }

    @Override
    public void setData_id(String data_id) {
        super.setData_id(data_id);
        setMovie_data_id(data_id);
    }

    public String getType() {
        return Optional.ofNullable(this.type).orElse("movie");
    }

    /**
     * When multiple documents share the same movie data id (e.g. no unique index), keep the newest row for maps.
     */
    public static MovieResult pickNewer(MovieResult a, MovieResult b) {
        Date ua = a.getUpdated_at();
        Date ub = b.getUpdated_at();
        if( ua != null && ub != null ) {
            int c = ua.compareTo(ub);
            if( c != 0 ) {
                return c > 0 ? a : b;
            }
        } else if( ua != null ) {
            return a;
        } else if( ub != null ) {
            return b;
        }
        String ida = a.getId();
        String idb = b.getId();
        if( ida != null && idb != null ) {
            return ida.compareTo(idb) >= 0 ? a : b;
        }
        return b;
    }
}
