package com.kiwi.cryoems.bpm.movie.model;

import com.kiwi.cryoems.bpm.model.CryoDataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;

/**
 * 与 cyroems {@code com.cryo.model.dataset.MovieDataset} 字段命名一一对齐，写入同一 Mongo
 * 集合 {@code movies}。
 *
 * <p>BPM 端目前只在
 * {@link com.kiwi.cryoems.bpm.mdoc.activity.CryoemsWaitMotionReadyActivity}
 * 内被消费：按 {@code belonging_data + name} 反查 dataset，把 {@code id} 回填到
 * {@link com.kiwi.cryoems.bpm.mdoc.model.MdocTiltMeta#setDataId(String)}，等价于原
 * {@code MdocMotionWaitScheduler.ensureMovieLoaded} 的 bootstrap 行为。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("movies")
public class MovieDataset extends CryoDataEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Indexed
    private String belonging_data;

    private String path;
    private Date mtime;
    private String name;
}
