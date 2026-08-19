package com.kiwi.project.bpm.designer.agent.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * designer-agent-eval-cases.json 单条用例。
 */
@Data
public class DesignerAgentEvalCase {
    private String id;
    private String scenario;
    private String baseBpmnXml;
    private String selectedElementId;
    private boolean readOnly;
    private boolean autoConfirmPlan = true;
    private String expectedStage = "await_preview";
    private List<String> expectedComponentIds = new ArrayList<>();
    private boolean matchAllComponents;
    private List<String> expectedFragments = new ArrayList<>();
    private List<String> forbiddenFragments = new ArrayList<>();
    private List<String> keywordHints = new ArrayList<>();
    private int minScore = 6;
    private int maxScore = 12;
    private int maxRepairRounds = 3;
    private boolean allowPartial;

    @Override
    public String toString() {
        return id;
    }
}
