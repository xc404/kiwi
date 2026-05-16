package com.kiwi.project.bpm.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BpmProcessInstanceBatchIdsRequest {

    /** Camunda 流程实例 id 列表，顺序保留；服务端会去重并限制条数上限 */
    private List<String> instanceIds = new ArrayList<>();
}
