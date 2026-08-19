package com.kiwi.bpmn.designer.agent.present;

import lombok.Data;

/**
 * 单条用户可读 Plan 步骤。
 */
@Data
public class PlanStepView {
    private int index;
    /** add | update | remove | connect | meta */
    private String kind;
    private String title;
    private String detail;
    private String targetRef;
}
