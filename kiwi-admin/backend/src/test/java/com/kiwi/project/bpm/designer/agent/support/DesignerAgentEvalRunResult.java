package com.kiwi.project.bpm.designer.agent.support;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次 Agent run 评测结构化结果。
 */
@Data
public class DesignerAgentEvalRunResult {
    private String caseId;
    private String scenario;
    private String targetProcessId;
    private String runId;
    private boolean passed;
    private int score;
    private int maxScore;
    private long elapsedMs;
    private String stage;
    private Boolean planSkipped;
    private int repairRoundsObserved;
    private String dispatchHint;
    private List<String> componentIdsUsed = new ArrayList<>();
    private List<Map<String, String>> issues = new ArrayList<>();
    private Map<String, Integer> scoreBreakdown = new LinkedHashMap<>();
    private List<String> failureReasons = new ArrayList<>();
    private List<String> sseEventTypes = new ArrayList<>();
    private String assistantReply;
    private String candidateXml;
    private String editPlanJson;
    private String issuesJson;
    private String errorMessage;
}
