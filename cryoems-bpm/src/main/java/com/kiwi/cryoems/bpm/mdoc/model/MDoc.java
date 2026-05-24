package com.kiwi.cryoems.bpm.mdoc.model;

import com.kiwi.cryoems.bpm.model.CryoDataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 与 cyroems {@code com.cryo.model.dataset.MDoc} 字段命名对齐，写入同一 Mongo 集合 {@code mdoc}。
 *
 * <p>BPM 端按"瘦契约"启动后，由 {@link com.kiwi.cryoems.bpm.mdoc.activity.CryoemsMdocStackActivity}
 * 等 {@link org.camunda.bpm.engine.delegate.JavaDelegate} 通过 {@code mdoc.dataId} 直连回查；
 * 字段集仅保留 stack / coarse-align 阶段必须的子集，避免与 cryoEMS 完整 schema 强耦合。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("mdoc")
public class MDoc extends CryoDataEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Indexed
    private String belonging_data;

    private String path;
    private Date mtime;
    private String name;
    private MdocMeta meta;
    private List<String> movie_data_ids;
    private boolean manualRebuild;
}
