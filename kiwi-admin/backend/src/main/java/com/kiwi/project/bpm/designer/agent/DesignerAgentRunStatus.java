package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentChatMessage;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent run 对外状态（REST / 轮询）。
 */
@Data
public class DesignerAgentRunStatus {
    private String runId;
    private String targetProcessId;
    private boolean active;
    private String stage;
    private String editPlanJson;
    private String planDisplayJson;
    private String candidateXml;
    private String assistantReply;
    private String askMessage;
    private String pluginHintJson;
    private String clarificationFormJson;
    private String pendingHitlItemsJson;
    private String issuesJson;
    private String errorMessage;
    private Boolean planSkipped;
    /** 持久化聊天气泡，打开设计器时恢复 */
    private List<DesignerAgentChatMessage> messages = new ArrayList<>();

    /**
     * 最近一次人机闸门操作结果（仅 POST /actions 响应携带，供前端处理预览画布落库/回退）。
     * {@code true}=接受，{@code false}=拒绝，未涉及闸门时为 null。
     */
    private Boolean gateAccepted;
}
