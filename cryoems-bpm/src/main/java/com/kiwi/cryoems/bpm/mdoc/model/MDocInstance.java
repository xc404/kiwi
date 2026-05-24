package com.kiwi.cryoems.bpm.mdoc.model;

import com.kiwi.cryoems.bpm.model.CryoDataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

/**
 * 与 cyroems {@code com.cryo.model.tilt.MDocInstance} 对齐，写入同一 Mongo 集合 {@code mDocInstance}。
 *
 * <p>BPM 端只保留 stack / coarse-align 等 delegate 需要的最小字段集：
 * {@code task_id}、{@code data_id}（指向 {@link MDoc#getId()}）、{@code name}（mdoc 实例展示名，
 * 与 {@code FilePathService#getMdocWorkDir} 的子目录名一致）以及 Kiwi 流程实例 id。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("mDocInstance")
public class MDocInstance extends CryoDataEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Indexed
    private String task_id;
    private String task_name;
    private String data_id;
    private String name;

    private String external_workflow_instance_id;
    private String currentActivity;
}
