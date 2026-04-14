package com.kiwi.project.bpm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 与前端 {@code CamundaElementModel#setValue} 命名空间一致，用于从 BPMN 还原到画布。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BpmComponentPropertySnapshotEntry
{
    /** 如 inputParameter、outputParameter、In、Out、property、taskAttr */
    private String namespace;
    private String key;
    /** 允许空串 */
    private String value;
}
