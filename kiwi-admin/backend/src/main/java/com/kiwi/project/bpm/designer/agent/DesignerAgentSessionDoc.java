package com.kiwi.project.bpm.designer.agent;

import com.kiwi.bpmn.designer.agent.runtime.DesignerAgentChatMessage;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** 流程级 Agent 会话索引（targetProcessId → runId + 聊天气泡）。 */
@Data
@Document("designer_agent_session")
public class DesignerAgentSessionDoc {

    @Id
    private String targetProcessId;
    private String runId;
    private List<DesignerAgentChatMessage> messages = new ArrayList<>();
    private Date updatedAt;
}
