package com.kiwi.project.ai;

import com.kiwi.project.ai.authoring.AiAuthoringProcessService;
import com.kiwi.project.ai.authoring.AiAuthoringVariables;
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
 * 当 {@code kiwi.ai.workflow-authoring.enabled=true} 且为设计器会话时，分流到内部编排流程。
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
    private final ObjectProvider<AiAuthoringProcessService> authoringProcessServiceProvider;

    public AiAssistantService(
            @Qualifier("kiwiChatClient") ObjectProvider<ChatClient> kiwiAssistantChatClientProvider,
            AiChatProperties properties,
            AssistantClientActionContext assistantClientActionContext,
            ObjectProvider<AiAuthoringProcessService> authoringProcessServiceProvider) {
        this.kiwiAssistantChatClientProvider = kiwiAssistantChatClientProvider;
        this.properties = properties;
        this.assistantClientActionContext = assistantClientActionContext;
        this.authoringProcessServiceProvider = authoringProcessServiceProvider;
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
        AiAssistantResponse authoring = tryScenarioAuthoring(springMessages, bpmDesignerSession);
        if (authoring != null) {
            return authoring;
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
    private AiAssistantResponse tryScenarioAuthoring(List<Message> springMessages, boolean bpmDesignerSession) {
        boolean flag = properties.getWorkflowAuthoring().isEnabled();
        if (!flag || !bpmDesignerSession) {
            log.debug("authoring skip: enabled={} bpmDesignerSession={}", flag, bpmDesignerSession);
            return null;
        }
        AiAuthoringProcessService authoring = authoringProcessServiceProvider.getIfAvailable();
        if (authoring == null || !authoring.isEnabled()) {
            log.warn("authoring skip: AiAuthoringProcessService unavailable or disabled (beanNull={})",
                    authoring == null);
            return null;
        }
        String lastUser = lastUserMessageText(springMessages);
        if (lastUser == null || lastUser.isBlank()) {
            log.debug("authoring skip: empty last user message");
            return null;
        }
        String processId = extractProcessId(springMessages);
        if (processId == null || processId.isBlank()) {
            log.warn("authoring skip: processId missing from designer system context");
            return null;
        }
        String selected = extractSelectedElementId(springMessages);
        String baseXml = extractCurrentBpmnXml(springMessages);
        boolean autoSave = resolveAutoSaveCanvas(springMessages);
        log.info("authoring start: targetProcessId={} selected={} scenarioLen={} baseXmlLen={} autoSave={}",
                processId, selected, lastUser.length(),
                baseXml == null ? 0 : baseXml.length(), autoSave);
        try {
            AiAuthoringProcessService.StartResult started =
                    authoring.start(lastUser, processId, selected, baseXml);
            List<ClientAction> canvasActions = buildAuthoringCanvasActions(started, autoSave);
            if (autoSave) {
                started = autoCompletePreviewSave(authoring, started);
            }
            log.info("authoring started: instanceId={} stage={} hasReply={} taskCount={}",
                    started.getProcessInstanceId(),
                    started.getStage(),
                    StringUtils.isNotBlank(started.getAssistantReply()),
                    started.getTasks() == null ? 0 : started.getTasks().size());
            AiAssistantResponse out = new AiAssistantResponse();
            out.setContent(buildAuthoringUserReply(started, autoSave));
            out.setActions(canvasActions);
            return out;
        } catch (ResponseStatusException e) {
            log.error("authoring start failed: {}", e.getReason(), e);
            AiAssistantResponse out = new AiAssistantResponse();
            out.setContent("AI 写工作流未能完成：\n" + StringUtils.defaultIfBlank(e.getReason(), e.getMessage()));
            out.setActions(List.of());
            return out;
        } catch (Exception e) {
            log.error("authoring start failed", e);
            AiAssistantResponse out = new AiAssistantResponse();
            out.setContent("AI 写工作流未能完成：" + e.getMessage());
            out.setActions(List.of());
            return out;
        }
    }

    private static String buildAuthoringUserReply(
            AiAuthoringProcessService.StartResult started, boolean autoSave) {
        StringBuilder content = new StringBuilder();
        if (StringUtils.isNotBlank(started.getAssistantReply())) {
            content.append(started.getAssistantReply().trim());
        } else {
            content.append("已根据你的描述更新工作流。");
        }
        String stage = started.getStage();
        if (StringUtils.isNotBlank(started.getCandidateXml())) {
            if (autoSave) {
                content.append("\n\n已将修改应用到画布并保存。");
            } else {
                content.append("\n\n已将修改导入画布预览（尚未保存）。");
            }
        }
        if (AiAuthoringVariables.StageAwaitPreview.equals(stage) && !autoSave) {
            content.append("请在右上角「AI 写工作流」面板确认保存或拒绝。");
        } else if (AiAuthoringVariables.StageAwaitAsk.equals(stage)) {
            content.append("\n\n");
            content.append(StringUtils.defaultIfBlank(started.getAskMessage(), "还需要你补充一些信息。"));
            content.append("\n请在右上角面板填写说明后提交。");
        } else if (AiAuthoringVariables.StageAwaitInstall.equals(stage)) {
            content.append("\n\n需要安装插件后才能继续。");
            if (StringUtils.isNotBlank(started.getPluginHintJson())) {
                content.append("\n提示：").append(started.getPluginHintJson());
            }
            content.append("\n请在右上角面板确认或拒绝安装。");
        } else if (StringUtils.isNotBlank(started.getAskMessage())) {
            content.append("\n\n").append(started.getAskMessage());
        }
        return content.toString().trim();
    }

    private static List<ClientAction> buildAuthoringCanvasActions(
            AiAuthoringProcessService.StartResult started, boolean autoSave) {
        String xml = started.getCandidateXml();
        if (StringUtils.isBlank(xml)) {
            return List.of();
        }
        return List.of(autoSave ? ClientAction.bpmnXml(xml) : ClientAction.bpmnXmlPreview(xml));
    }

    /**
     * 自动保存开启时：完成预览 User Task，触发 SaveDelegate 落库，避免面板一直等待确认。
     */
    private AiAuthoringProcessService.StartResult autoCompletePreviewSave(
            AiAuthoringProcessService authoring, AiAuthoringProcessService.StartResult started) {
        if (!AiAuthoringVariables.StageAwaitPreview.equals(started.getStage())
                || started.getTasks() == null
                || started.getTasks().isEmpty()) {
            return started;
        }
        return started.getTasks().stream()
                .filter(t -> "UserTask_Preview".equals(t.getTaskDefinitionKey()))
                .findFirst()
                .map(t -> {
                    try {
                        AiAuthoringProcessService.StatusResult after = authoring.completeTask(
                                t.getId(), Map.of(AiAuthoringVariables.PreviewConfirmed, true));
                        AiAuthoringProcessService.StartResult merged = new AiAuthoringProcessService.StartResult();
                        merged.setProcessInstanceId(after.getProcessInstanceId());
                        merged.setBusinessKey(after.getBusinessKey());
                        merged.setTargetProcessId(after.getTargetProcessId());
                        merged.setActive(after.isActive());
                        merged.setStage(after.getStage());
                        merged.setDispatchCode(after.getDispatchCode());
                        merged.setCandidateXml(
                                StringUtils.isNotBlank(after.getCandidateXml())
                                        ? after.getCandidateXml()
                                        : started.getCandidateXml());
                        merged.setAssistantReply(
                                StringUtils.isNotBlank(after.getAssistantReply())
                                        ? after.getAssistantReply()
                                        : started.getAssistantReply());
                        merged.setAskMessage(after.getAskMessage());
                        merged.setPluginHintJson(after.getPluginHintJson());
                        merged.setIssuesJson(after.getIssuesJson());
                        merged.setCatalogJson(after.getCatalogJson());
                        merged.setTasks(after.getTasks());
                        merged.setVariables(after.getVariables());
                        return merged;
                    } catch (Exception e) {
                        log.warn("auto-complete preview save failed: {}", e.getMessage());
                        return started;
                    }
                })
                .orElse(started);
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
