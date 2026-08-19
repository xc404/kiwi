package com.kiwi.project.ai;

import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.WriteWorkflowIntent;
import com.kiwi.bpmn.assistant.WriteWorkflowStatus;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.ai.assistant.AssistantIntentService;
import com.kiwi.project.ai.assistant.WriteWorkflowSessionService;
import com.kiwi.project.ai.mcp.KiwiAdminAiMcpConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 助手对话：基于统一 {@link ChatClient}（{@code kiwiChatClient}）与 MCP 工具；由模型自行选用工具。
 * 前端动作由 {@link AssistantClientActionContext} 收集（菜单跳转、BPM 设计器建议等）。
 * 当 {@code kiwi.ai.write-workflow.enabled=true} 且为设计器会话时：意图 talk|modify，
 * modify 走 {@link WriteWorkflowSessionService} Java 管线。
 */
@Slf4j
@Service
public class AiAssistantService {

    private static final String TOOL_ASSISTANT_DESIGNER_BPMN_XML = "assistant_designer_bpmn_xml";

    private static final Pattern BPM_DESIGNER_FALSE_SUCCESS = Pattern.compile(
            "已成功|成功完成|已更新|已复制|已成功移除|已移除|已删除|精准复制|画布.*已|节点.*已.*更新|仅剩");

    private static final Pattern BPM_DESIGNER_USER_EDIT_INTENT = Pattern.compile(
            "移除|删除|去掉|添加|追加|复制|修改|更新|改|连接|部署|导出|保存|插入|替换");

    private static final Pattern BPM_DESIGNER_NEEDS_SOURCE_PROCESS = Pattern.compile(
            "复制|从.{0,30}流程|源流程|其它流程|其他流程|bpmPd|CryoEMS|cryoems", Pattern.CASE_INSENSITIVE);

    private static final Pattern ProcessIdInContext = Pattern.compile("processId:\\s*(\\S+)");

    private static final String BPM_DESIGNER_RETRY_USER = """
            上轮未登记任何画布动作（actions 为空）。用户请求的是修改 BPMN 流程图。
            请根据 system 中的「当前 BPMN XML」完成编辑，并必须调用 assistant_designer_bpmn_xml 提交完整 definitions XML；
            不要只在文本中描述已删除/已修改/已成功。若需从其它流程复制配置，可先 bpmPd_get 再 assistant_designer_bpmn_xml。
            若仅追加组件则用 assistant_designer_match_component。""";

    private final ObjectProvider<ChatClient> kiwiAssistantChatClientProvider;
    private final AiChatProperties properties;
    private final AssistantClientActionContext assistantClientActionContext;
    private final ObjectProvider<WriteWorkflowSessionService> writeWorkflowSessionServiceProvider;
    private final ObjectProvider<AssistantIntentService> intentServiceProvider;
    private final SessionService sessionService;
    private final DesignerAgentProperties designerAgentProperties;

    public AiAssistantService(
            @Qualifier("kiwiChatClient") ObjectProvider<ChatClient> kiwiAssistantChatClientProvider,
            AiChatProperties properties,
            AssistantClientActionContext assistantClientActionContext,
            ObjectProvider<WriteWorkflowSessionService> writeWorkflowSessionServiceProvider,
            ObjectProvider<AssistantIntentService> intentServiceProvider,
            SessionService sessionService,
            DesignerAgentProperties designerAgentProperties) {
        this.kiwiAssistantChatClientProvider = kiwiAssistantChatClientProvider;
        this.properties = properties;
        this.assistantClientActionContext = assistantClientActionContext;
        this.writeWorkflowSessionServiceProvider = writeWorkflowSessionServiceProvider;
        this.intentServiceProvider = intentServiceProvider;
        this.sessionService = sessionService;
        this.designerAgentProperties = designerAgentProperties;
    }

    public AiAssistantResponse run(List<AiChatMessage> messages) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI 对话未启用（kiwi.ai.enabled=false）");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空");
        }

        List<Message> springMessages = new ArrayList<>();
        for (AiChatMessage m : messages) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            springMessages.add(toSpringMessage(m));
        }
        if (springMessages.isEmpty()) {
            throw new IllegalArgumentException("没有有效的对话内容");
        }

        boolean bpmDesignerSession = isBpmDesignerSession(springMessages);
        AiAssistantResponse writeWorkflow = tryWriteWorkflow(springMessages, bpmDesignerSession);
        if (writeWorkflow != null) {
            return writeWorkflow;
        }

        String systemPrompt = KiwiAdminAiMcpConfiguration.SYSTEM_PROMPT;
        if (bpmDesignerSession) {
            systemPrompt = systemPrompt + "\n\n" + KiwiAdminAiMcpConfiguration.BPM_DESIGNER_SUPPLEMENT;
        }

        assistantClientActionContext.beginRequest();
        String content = callAssistant(systemPrompt, springMessages, null);
        List<ClientAction> actions = assistantClientActionContext.drainActions();

        if (bpmDesignerSession && actions.isEmpty() && shouldRetryBpmDesignerEdit(springMessages, content)) {
            List<Message> retryMessages = new ArrayList<>(springMessages);
            retryMessages.add(new AssistantMessage(content != null ? content : ""));
            retryMessages.add(new UserMessage(BPM_DESIGNER_RETRY_USER));

            ToolCallingChatOptions.Builder retryOptionsBuilder = null;
            String lastUser = lastUserMessageText(springMessages);
            if (lastUser != null && !BPM_DESIGNER_NEEDS_SOURCE_PROCESS.matcher(lastUser).find()) {
                retryOptionsBuilder = ToolCallingChatOptions.builder();
            }

            assistantClientActionContext.beginRequest();
            String retryContent = callAssistant(systemPrompt, retryMessages, retryOptionsBuilder);
            List<ClientAction> retryActions = assistantClientActionContext.drainActions();
            if (!retryActions.isEmpty()) {
                content = retryContent;
                actions = retryActions;
            }
        }

        if (content == null || content.isBlank()) {
            content = "（模型未返回文本，请重试。）";
        }

        AiAssistantResponse out = new AiAssistantResponse();
        out.setContent(content.trim());
        out.setActions(actions);
        appendBpmDesignerActionWarningIfNeeded(out, bpmDesignerSession, actions);
        return out;
    }

    @Nullable
    @Deprecated(since = "2026-08", forRemoval = true)
    private AiAssistantResponse tryWriteWorkflow(List<Message> springMessages, boolean bpmDesignerSession) {
        if (designerAgentProperties.isEnabled()) {
            log.debug("write-workflow skip: designer-agent enabled");
            return null;
        }
        boolean flag = properties.getWriteWorkflow().isEnabled();
        if (!flag || !bpmDesignerSession) {
            log.debug("write-workflow skip: enabled={} bpmDesignerSession={}", flag, bpmDesignerSession);
            return null;
        }
        WriteWorkflowSessionService sessions = writeWorkflowSessionServiceProvider.getIfAvailable();
        if (sessions == null || !sessions.isEnabled()) {
            log.warn("write-workflow skip: WriteWorkflowSessionService unavailable or disabled (beanNull={})",
                    sessions == null);
            return null;
        }
        String lastUser = lastUserMessageText(springMessages);
        if (lastUser == null || lastUser.isBlank()) {
            return null;
        }
        String processId = extractProcessId(springMessages);
        if (processId == null || processId.isBlank()) {
            log.warn("write-workflow skip: processId missing from designer system context");
            return null;
        }

        boolean autoSave = resolveAutoSaveCanvas(springMessages);
        try {
            WriteWorkflowStatus correlated = sessions.correlateIfWaiting(processId, lastUser);
            if (correlated != null) {
                if (autoSave && AssistantVariables.StageAwaitPreview.equals(correlated.getStage())) {
                    correlated = sessions.confirmPreview(correlated.getSessionId(), true);
                }
                String reply = buildWriteWorkflowUserReply(correlated, autoSave);
                if ((AssistantVariables.StageAwaitPreview.equals(correlated.getStage())
                        || AssistantVariables.StageAwaitInstall.equals(correlated.getStage()))
                        && !autoSave) {
                    reply = reply + "\n\n请在右上角「AI 写工作流」面板操作，或回复「确认」/「拒绝」。";
                }
                log.info("write-workflow correlated: sessionId={} stage={}",
                        correlated.getSessionId(), correlated.getStage());
                AiAssistantResponse out = new AiAssistantResponse();
                out.setContent(reply);
                out.setActions(buildWriteWorkflowCanvasActions(correlated, autoSave));
                return out;
            }

            AssistantIntentService intentService = intentServiceProvider.getIfAvailable();
            WriteWorkflowIntent intent = intentService != null
                    ? intentService.determineIntent(springMessages)
                    : WriteWorkflowIntent.Modify;
            if (intent == WriteWorkflowIntent.Talk) {
                log.debug("write-workflow intent=talk → ChatClient");
                return null;
            }

            String selected = extractSelectedElementId(springMessages);
            String baseXml = extractCurrentBpmnXml(springMessages);
            String initiatorUserId = sessionService.getCurrentUser() != null
                    ? sessionService.getCurrentUser().getId()
                    : null;
            log.info("write-workflow start: targetProcessId={} scenarioLen={} autoSave={}",
                    processId, lastUser.length(), autoSave);
            WriteWorkflowStatus started = sessions.start(
                    lastUser, processId, selected, baseXml, initiatorUserId);
            if (autoSave && AssistantVariables.StageAwaitPreview.equals(started.getStage())) {
                started = sessions.confirmPreview(started.getSessionId(), true);
            }
            log.info("write-workflow done turn: sessionId={} stage={}",
                    started.getSessionId(), started.getStage());
            AiAssistantResponse out = new AiAssistantResponse();
            out.setContent(buildWriteWorkflowUserReply(started, autoSave));
            out.setActions(buildWriteWorkflowCanvasActions(started, autoSave));
            return out;
        } catch (ResponseStatusException e) {
            log.error("write-workflow failed: {}", e.getReason(), e);
            AiAssistantResponse out = new AiAssistantResponse();
            out.setContent("AI 写工作流未能完成：\n" + StringUtils.defaultIfBlank(e.getReason(), e.getMessage()));
            out.setActions(List.of());
            return out;
        } catch (Exception e) {
            log.error("write-workflow failed", e);
            AiAssistantResponse out = new AiAssistantResponse();
            out.setContent("AI 写工作流未能完成：" + e.getMessage());
            out.setActions(List.of());
            return out;
        }
    }

    private static String buildWriteWorkflowUserReply(WriteWorkflowStatus status, boolean autoSave) {
        StringBuilder content = new StringBuilder();
        if (StringUtils.isNotBlank(status.getAssistantReply())) {
            content.append(status.getAssistantReply().trim());
        } else {
            content.append("已根据你的描述更新工作流。");
        }
        String stage = status.getStage();
        if (StringUtils.isNotBlank(status.getCandidateXml())) {
            if (autoSave && AssistantVariables.StageDone.equals(stage)) {
                content.append("\n\n已将修改应用到画布并保存。");
            } else if (StringUtils.isNotBlank(status.getCandidateXml())) {
                content.append("\n\n已将修改导入画布预览（尚未保存）。");
            }
        }
        if (AssistantVariables.StageAwaitPreview.equals(stage) && !autoSave) {
            content.append("请在右上角「AI 写工作流」面板确认保存或拒绝。");
        } else if (AssistantVariables.StageAwaitAsk.equals(stage)) {
            content.append("\n\n");
            content.append(StringUtils.defaultIfBlank(status.getAskMessage(), "还需要你补充一些信息。"));
            content.append("\n请在右上角面板填写说明后提交，或直接在对话中回复。");
        } else if (AssistantVariables.StageAwaitInstall.equals(stage)) {
            content.append("\n\n需要安装插件后才能继续。");
            if (StringUtils.isNotBlank(status.getPluginHintJson())) {
                content.append("\n提示：").append(status.getPluginHintJson());
            }
            content.append("\n请在右上角面板确认或拒绝安装。");
        } else if (StringUtils.isNotBlank(status.getAskMessage())) {
            content.append("\n\n").append(status.getAskMessage());
        }
        return content.toString().trim();
    }

    private static List<ClientAction> buildWriteWorkflowCanvasActions(
            WriteWorkflowStatus status, boolean autoSave) {
        String xml = status.getCandidateXml();
        if (StringUtils.isBlank(xml)) {
            return List.of();
        }
        return List.of(autoSave ? ClientAction.bpmnXml(xml) : ClientAction.bpmnXmlPreview(xml));
    }

    /** 是否自动保存：只认设计器 system 上下文里的 aiAuthoringAutoSave（前端开关） */
    private boolean resolveAutoSaveCanvas(List<Message> springMessages) {
        Boolean fromFrontend = extractAutoSaveFlag(springMessages);
        return fromFrontend == null || fromFrontend;
    }

    @Nullable
    private static Boolean extractAutoSaveFlag(List<Message> messages) {
        Pattern p = Pattern.compile("aiAuthoringAutoSave:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        for (Message m : messages) {
            if (m instanceof SystemMessage sm && sm.getText() != null) {
                Matcher matcher = p.matcher(sm.getText());
                if (matcher.find()) {
                    return Boolean.parseBoolean(matcher.group(1));
                }
            }
        }
        return null;
    }

    @Nullable
    private static String extractCurrentBpmnXml(List<Message> messages) {
        for (Message m : messages) {
            if (!(m instanceof SystemMessage sm) || sm.getText() == null) {
                continue;
            }
            String text = sm.getText();
            int marker = text.indexOf("当前 BPMN XML:");
            if (marker < 0) {
                continue;
            }
            int fence = text.indexOf("```xml", marker);
            if (fence < 0) {
                fence = text.indexOf("```", marker);
            }
            if (fence < 0) {
                continue;
            }
            int start = text.indexOf('\n', fence);
            if (start < 0) {
                continue;
            }
            int end = text.indexOf("```", start + 1);
            if (end < 0) {
                continue;
            }
            String xml = text.substring(start + 1, end).trim();
            if (xml.isBlank() || xml.startsWith("（空）") || xml.startsWith("(")) {
                return null;
            }
            // 截断标记的上下文不可用
            if (xml.contains("已截断")) {
                return null;
            }
            return xml;
        }
        return null;
    }

    @Nullable
    private static String extractProcessId(List<Message> messages) {
        for (Message m : messages) {
            if (m instanceof SystemMessage sm && sm.getText() != null) {
                Matcher matcher = ProcessIdInContext.matcher(sm.getText());
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
        }
        return null;
    }

    @Nullable
    private static String extractSelectedElementId(List<Message> messages) {
        Pattern p = Pattern.compile("selectedElementId:\\s*(\\S+)");
        for (Message m : messages) {
            if (m instanceof SystemMessage sm && sm.getText() != null) {
                Matcher matcher = p.matcher(sm.getText());
                if (matcher.find()) {
                    String v = matcher.group(1).trim();
                    if (!v.startsWith("（")) {
                        return v;
                    }
                }
            }
        }
        return null;
    }

    private String callAssistant(
            String systemPrompt, List<Message> messages, @Nullable ChatOptions.Builder<?> optionsBuilder) {
        var spec = kiwiAssistantChatClientProvider.getObject()
                .prompt()
                .system(systemPrompt)
                .messages(messages);
        if (optionsBuilder != null) {
            spec = spec.options(optionsBuilder);
        }
        return spec.call().content();
    }

    private static Map<String, Object> forcedBpmnXmlToolChoice() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", TOOL_ASSISTANT_DESIGNER_BPMN_XML);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("type", "function");
        choice.put("function", function);
        return choice;
    }

    private static boolean shouldRetryBpmDesignerEdit(List<Message> messages, @Nullable String assistantContent) {
        String lastUser = lastUserMessageText(messages);
        if (lastUser != null && BPM_DESIGNER_USER_EDIT_INTENT.matcher(lastUser).find()) {
            return true;
        }
        return assistantContent != null && BPM_DESIGNER_FALSE_SUCCESS.matcher(assistantContent).find();
    }

    @Nullable
    private static String lastUserMessageText(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m instanceof UserMessage um) {
                return um.getText();
            }
        }
        return null;
    }

    private void appendBpmDesignerActionWarningIfNeeded(
            AiAssistantResponse out, boolean bpmDesignerSession, List<ClientAction> actions) {
        if (!bpmDesignerSession || actions == null || !actions.isEmpty()) {
            return;
        }
        String text = out.getContent();
        if (text == null || !BPM_DESIGNER_FALSE_SUCCESS.matcher(text).find()) {
            return;
        }
        out.setContent(text
                + "\n\n⚠️ 本轮未登记画布动作（actions 为空），流程图可能未实际变更。"
                + "请重试并确保调用 assistant_designer_bpmn_xml 登记完整 BPMN XML。");
    }

    private static boolean isBpmDesignerSession(List<Message> messages) {
        for (Message m : messages) {
            if (m instanceof SystemMessage sm) {
                String text = sm.getText();
                if (text != null && text.contains("BPM 流程设计器")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Message toSpringMessage(AiChatMessage m) {
        String role = m.getRole() == null ? "user" : m.getRole().trim().toLowerCase();
        String c = m.getContent();
        return switch (role) {
            case "system" -> new SystemMessage(c);
            case "assistant" -> new AssistantMessage(c);
            default -> new UserMessage(c);
        };
    }
}
