package com.kiwi.project.ai.conversation;

import com.kiwi.common.entity.BaseEntity;
import com.kiwi.project.ai.AiChatMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手会话（按用户与 scope 隔离；仅持久化 user/assistant 消息）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("ai_chat_conversation")
public class AiChatConversation extends BaseEntity<String> {

    public static final String SCOPE_GLOBAL = "global";
    public static final String SCOPE_BPM_DESIGNER = "bpm-designer";

    /** 所属用户 ID */
    private String ownerId;

    /** global | bpm-designer */
    private String scope;

    /** 可选上下文键，如 BPM processId */
    private String scopeRef;

    private String title;

    /** 供关键字检索的拼接摘要 */
    private String searchText;

    private String lastMessagePreview;

    private int messageCount;

    private List<AiChatMessage> messages = new ArrayList<>();
}
