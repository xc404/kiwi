package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cursor 式变更计划：有序 operations，确定性 patch 到 BPMN。
 */
@Data
public class EditPlan {
    private String processId;
    private String summary;
    private List<EditOperation> operations = new ArrayList<>();
}
