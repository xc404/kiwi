package com.kiwi.cryoems.bpm.mdoc.model;

import com.kiwi.cryoems.bpm.mdoc.result.AlignReconResult;
import com.kiwi.cryoems.bpm.mdoc.result.CoarseAlignrResult;
import com.kiwi.cryoems.bpm.mdoc.result.ExcludeResult;
import com.kiwi.cryoems.bpm.mdoc.result.PatchTrackingResult;
import com.kiwi.cryoems.bpm.mdoc.result.SeriesAlignResult;
import com.kiwi.cryoems.bpm.mdoc.result.StackResult;
import com.kiwi.cryoems.bpm.model.CryoDataEntity;
import com.kiwi.cryoems.bpm.movie.model.MovieImage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 与 cyroems {@code com.cryo.model.MDocResult} 字段命名严格对齐，写入同一 Mongo 集合 {@code mDocResult}。
 *
 * <p>承载 cryo-em mdoc 流水线所有阶段的结构化产物：
 * <ul>
 *     <li>基础键：{@code task_data_id} / {@code task_id} / {@code instance_id} /
 *         {@code data_id} / {@code config_id} / {@code category} —— 等价 cyroems
 *         {@code com.cryo.model.InstanceResult}；</li>
 *     <li>{@code meta} —— 由 {@link MDoc#getMeta()} 镜像；</li>
 *     <li>{@code stackResult} / {@code orgStackResult} / {@code excudedResult} —— stack 阶段
 *         （注意 cyroems {@code excudedResult} 拼写历史保留）；</li>
 *     <li>{@code coarseAlignrResult} / {@code patchTrackingResult} / {@code seriesAlignResult} /
 *         {@code alignReconResult} —— 重建阶段全部子结果，与 {@code script/mdoc_reconstruct.sh}
 *         13 段命令产物路径一一对应；</li>
 *     <li>{@code images} —— mdoc_recon 缩略图三视图等；</li>
 *     <li>{@code rate} —— 默认 0；</li>
 *     <li>{@code files} —— cyroems 端在部分阶段写入的辅助文件清单（保留兼容）。</li>
 * </ul></p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("mDocResult")
@SuppressWarnings("checkstyle:MemberName")
public class MdocResult extends CryoDataEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Indexed
    private String task_data_id;

    @Indexed
    private String task_id;

    @Indexed
    private String instance_id;

    private String data_id;

    private String config_id;

    private String category;

    private MdocMeta meta;

    private StackResult stackResult;

    private CoarseAlignrResult coarseAlignrResult;

    private PatchTrackingResult patchTrackingResult;

    private SeriesAlignResult seriesAlignResult;

    private AlignReconResult alignReconResult;

    private Map<MovieImage.Type, MovieImage> images;

    private int rate = 0;

    /**
     * stack_and_filter 之前的"原始" StackResult。当过滤导致 {@link StackResult#getFiles()} 与
     * 输入差异时，cyroems 会把过滤前快照写到这里以便回溯。
     */
    private StackResult orgStackResult;

    /**
     * cyroems 历史拼写为 {@code excudedResult}（缺一个 'l'），保留以保证 Mongo 字段名与既有数据
     * 互相兼容；不要重命名为 {@code excludedResult}。
     */
    private ExcludeResult excudedResult;

    public Map<MovieImage.Type, MovieImage> getImages() {
        if (this.images == null) {
            this.images = new HashMap<>();
        }
        return this.images;
    }

    public void addImage(MovieImage movieImage) {
        getImages().put(movieImage.getType(), movieImage);
    }
}
