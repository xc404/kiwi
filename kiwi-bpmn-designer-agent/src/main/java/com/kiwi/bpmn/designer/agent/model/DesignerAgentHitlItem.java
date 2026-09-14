package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

/**
 * 决策队列条目（Cursor 式 HITL 基础契约；Graph 工具环 interrupt 后续接入）。
 */
@Data
public class DesignerAgentHitlItem {
    private String id;
    /** 例如 tool_approval、clarify、plan */
    private String kind;
    private String title;
    private String detail;
    /** 可选 JSON 载荷 */
    private String payloadJson;
}
