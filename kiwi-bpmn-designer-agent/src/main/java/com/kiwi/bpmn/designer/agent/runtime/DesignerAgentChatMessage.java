package com.kiwi.bpmn.designer.agent.runtime;

import lombok.Data;

/** 持久化/UI 复用的单条聊天消息。 */
@Data
public class DesignerAgentChatMessage {
    private String role;
    private String text;
}
