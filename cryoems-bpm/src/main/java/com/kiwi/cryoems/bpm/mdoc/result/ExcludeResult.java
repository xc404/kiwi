package com.kiwi.cryoems.bpm.mdoc.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 与 cyroems {@code com.cryo.task.tilt.filter.ExcludeResult} 字段命名严格一致。
 *
 * <ul>
 *     <li>{@code plotImg} —— excluded plot 图像路径</li>
 *     <li>{@code exclude_thumbnails} —— excluded 缩略图集中目录</li>
 *     <li>{@code exclude_list} —— 被排除的 motion DW 文件路径列表</li>
 * </ul>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("checkstyle:MemberName")
public class ExcludeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String plotImg;
    private String exclude_thumbnails;
    private List<String> exclude_list;
}
