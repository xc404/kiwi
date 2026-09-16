package com.kiwi.bpmn.designer.harness;

import com.kiwi.bpmn.designer.harness.model.HarnessChatMessage;
import com.kiwi.bpmn.designer.harness.persist.DesignerHarnessSession;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DesignerHarnessTurnLlm {

    static final String SystemPrompt = """
            你是 Kiwi BPM 设计器里的对话助手，行为对齐 Cursor：根据对话决定说话或调用工具。
            只能使用这三个工具：search_components、get_component、apply_bpmn_ops。
            改图必须调用 apply_bpmn_ops，禁止在回复中输出 BPMN XML。
            添加 serviceTask 前必须 search_components 再 get_component，parameters 的 key 必须与契约一致。
            线性插入节点时 addNode 必须同时给 afterRef 与 beforeRef（前驱和后继），不要只给一边再重复 addFlow。
            当前流程图在本轮用户附件里；节点用 id 引用。
            工具会把校验问题返回给你，有问题就再 apply 一次，或用中文向用户说明。
            只聊天、不改图时不要调用 apply_bpmn_ops。
            """;

    private static final int HistoryMax = 20;
    private static final int BpmnMaxChars = 48_000;

    private final ObjectProvider<ChatClient> chatClientProvider;

    public DesignerHarnessTurnLlm(
            @Qualifier(DesignerHarnessChatClientConfiguration.BeanName) ObjectProvider<ChatClient> chatClientProvider) {
        this.chatClientProvider = chatClientProvider;
    }

    public String complete(DesignerHarnessSession session, String bpmnXml, String selectedElementId) {
        ChatClient client = chatClientProvider.getIfAvailable();
        if (client == null) {
            return "AI ChatClient 未配置";
        }
        List<Message> messages = toSpringMessages(session.getMessages());
        messages.add(new UserMessage(workspaceAttachment(bpmnXml, selectedElementId)));
        String content = client.prompt()
                .system(SystemPrompt)
                .messages(messages)
                .options(ToolCallingChatOptions.builder().internalToolExecutionEnabled(true))
                .call()
                .content();
        if (StringUtils.isBlank(content)) {
            return "（本轮没有文字回复。若已改图，请看画布。）";
        }
        return content.trim();
    }

    private List<Message> toSpringMessages(List<HarnessChatMessage> history) {
        List<HarnessChatMessage> source = history == null ? List.of() : history;
        int from = Math.max(0, source.size() - HistoryMax);
        List<Message> messages = new ArrayList<>();
        for (int i = from; i < source.size(); i++) {
            HarnessChatMessage m = source.get(i);
            if (m == null || StringUtils.isBlank(m.getText())) {
                continue;
            }
            if ("assistant".equals(m.getRole())) {
                messages.add(new AssistantMessage(m.getText()));
            } else {
                messages.add(new UserMessage(m.getText()));
            }
        }
        return messages;
    }

    private String workspaceAttachment(String bpmnXml, String selectedElementId) {
        String xml = StringUtils.defaultString(bpmnXml);
        if (xml.length() > BpmnMaxChars) {
            xml = xml.substring(0, BpmnMaxChars) + "\n…(截断)";
        }
        return """
                【本轮工作区，不是上一句对话】
                selectedElementId: %s
                当前 BPMN XML:
                %s
                """.formatted(StringUtils.defaultIfBlank(selectedElementId, "（无）"), xml);
    }
}