package com.kiwi.cryoems.bpm.model;

import com.kiwi.cryoems.bpm.model.cryosparc.CryosparcResult;
import com.kiwi.cryoems.bpm.model.ctf.EstimationResult;
import com.kiwi.cryoems.bpm.model.motion.MotionResult;
import com.kiwi.cryoems.bpm.model.vfm.VFMResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 与 cyroems {@code com.cryo.model.MovieResult} 对齐，写入同一 Mongo 集合 {@code movieResult}。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document
public class MovieResult extends CryoDataEntity {

//    @Indexed
//    private String task_data_id;
//
//    @Indexed
//    private String task_id;

    @Indexed
    private String instance_id;

//    private String data_id;

//    private String config_id;
//
//    private String category;
//
//    private String movie_data_id;

    private Map<MovieImage.Type, MovieImage> images;
    private MrcMetadata mrcMetadata;
    private MotionResult motion;
    private EstimationResult ctfEstimation;
    private VFMResult vfmResult;
//    private String type;

    public Map<MovieImage.Type, MovieImage> getImages() {
        if (this.images == null) {
            this.images = new HashMap<>();
        }
        return this.images;
    }

    public void addImage(MovieImage movieImage) {
        getImages().put(movieImage.getType(), movieImage);
    }

//    public String getData_id() {
//        return Optional.ofNullable(data_id).orElse(movie_data_id);
//    }
//
//    public void setData_id(String data_id) {
//        this.data_id = data_id;
//        this.movie_data_id = data_id;
//    }

//    public String getType() {
//        return Optional.ofNullable(this.type).orElse("movie");
//    }

    public static MovieResult pickNewer(MovieResult a, MovieResult b) {
        Date ua = a.getUpdated_at();
        Date ub = b.getUpdated_at();
        if (ua != null && ub != null) {
            int c = ua.compareTo(ub);
            if (c != 0) {
                return c > 0 ? a : b;
            }
        } else if (ua != null) {
            return a;
        } else if (ub != null) {
            return b;
        }
        String ida = a.getId();
        String idb = b.getId();
        if (ida != null && idb != null) {
            return ida.compareTo(idb) >= 0 ? a : b;
        }
        return b;
    }
}
