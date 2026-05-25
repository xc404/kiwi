package com.kiwi.project.bpm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@link com.kiwi.project.bpm.service.BpmComponentService#previewConflicts} 单条结果：
 * 按 {@code sourceKey} 检测与同请求内更早条目或库中已有组件的冲突。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BpmComponentPreviewConflictItem {

    private int index;
    private boolean conflict;
    /** 库中已有记录的 id；纯列表内重复时为 null */
    private String existingId;
    private String existingName;
    /** 回显草稿中的 sourceKey */
    private String sourceKey;
    /** 与同请求中 {@code [0, index)} 某条 sourceKey 重复时，指向首次出现的索引 */
    private Integer duplicateOfBatchIndex;
}
