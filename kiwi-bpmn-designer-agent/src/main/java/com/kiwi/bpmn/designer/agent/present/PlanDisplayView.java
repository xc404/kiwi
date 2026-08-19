package com.kiwi.bpmn.designer.agent.present;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * EditPlan 的用户可读展示视图（与执行 IR 分离）。
 */
@Data
public class PlanDisplayView {
    private String summary;
    private List<PlanStepView> steps = new ArrayList<>();
    private int operationCount;
}
