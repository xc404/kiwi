package com.kiwi.project.ai.assistant;

import com.kiwi.bpmn.assistant.AssistantProperties;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.bpmn.assistant.WriteWorkflowSession;
import com.kiwi.bpmn.assistant.WriteWorkflowStatus;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按目标流程维护写工作流会话；correlate 人机停顿。
 */
@Service
@RequiredArgsConstructor
public class WriteWorkflowSessionService {

    private final AssistantProperties assistantProperties;
    private final WriteWorkflowOrchestrator orchestrator;

    private final Map<String, WriteWorkflowSession> sessionsByTarget = new ConcurrentHashMap<>();
    private final Map<String, WriteWorkflowSession> sessionsById = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return assistantProperties.isEnabled();
    }

    public WriteWorkflowStatus start(
            String scenario,
            String targetProcessId,
            String selectedElementId,
            String baseBpmnXml,
            String initiatorUserId) {
        ensureEnabled();
        if (StringUtils.isBlank(scenario) || StringUtils.isBlank(targetProcessId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scenario 与 targetProcessId 不能为空");
        }
        clearByTarget(targetProcessId);
        WriteWorkflowSession session = WriteWorkflowSession.newSession(
                scenario, targetProcessId, selectedElementId, baseBpmnXml, initiatorUserId);
        try {
            orchestrator.runTurn(session);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 写工作流编排失败: " + e.getMessage(), e);
        }
        put(session);
        return session.toStatus();
    }

    public WriteWorkflowStatus statusByTarget(String targetProcessId) {
        WriteWorkflowSession session = sessionsByTarget.get(targetProcessId);
        if (session == null) {
            WriteWorkflowStatus empty = new WriteWorkflowStatus();
            empty.setTargetProcessId(targetProcessId);
            empty.setActive(false);
            return empty;
        }
        return session.toStatus();
    }

    public WriteWorkflowStatus statusBySessionId(String sessionId) {
        WriteWorkflowSession session = requireSession(sessionId);
        return session.toStatus();
    }

    public WriteWorkflowSession findActiveByTarget(String targetProcessId) {
        WriteWorkflowSession session = sessionsByTarget.get(targetProcessId);
        if (session == null || !session.isActive()) {
            return null;
        }
        return session;
    }

    public WriteWorkflowStatus answerAsk(String sessionId, String userAnswer) {
        WriteWorkflowSession session = requireSession(sessionId);
        if (!AssistantVariables.StageAwaitAsk.equals(session.getStage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前不在追问阶段: " + session.getStage());
        }
        if (StringUtils.isBlank(userAnswer)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userAnswer 不能为空");
        }
        try {
            orchestrator.continueAfterAsk(session, userAnswer.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "续跑失败: " + e.getMessage(), e);
        }
        put(session);
        return session.toStatus();
    }

    public WriteWorkflowStatus confirmPreview(String sessionId, boolean confirmed) {
        WriteWorkflowSession session = requireSession(sessionId);
        if (!AssistantVariables.StageAwaitPreview.equals(session.getStage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前不在预览阶段: " + session.getStage());
        }
        try {
            orchestrator.confirmPreview(session, confirmed);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "预览确认失败: " + e.getMessage(), e);
        }
        put(session);
        return session.toStatus();
    }

    public WriteWorkflowStatus confirmInstall(String sessionId, boolean accepted) {
        WriteWorkflowSession session = requireSession(sessionId);
        if (!AssistantVariables.StageAwaitInstall.equals(session.getStage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前不在安装确认阶段: " + session.getStage());
        }
        try {
            orchestrator.confirmInstall(session, accepted);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "安装确认失败: " + e.getMessage(), e);
        }
        put(session);
        return session.toStatus();
    }

    /**
     * Chat 桥接：若会话卡在 await_*，用本轮用户输入续跑。
     *
     * @return 续跑后的状态；若不在等待态返回 null
     */
    public WriteWorkflowStatus correlateIfWaiting(String targetProcessId, String userText) {
        WriteWorkflowSession session = findActiveByTarget(targetProcessId);
        if (session == null || !session.isAwaitingHuman()) {
            return null;
        }
        String stage = session.getStage();
        if (AssistantVariables.StageAwaitAsk.equals(stage)) {
            return answerAsk(session.getSessionId(), userText);
        }
        if (AssistantVariables.StageAwaitPreview.equals(stage)) {
            Boolean confirmed = parseYesNo(userText);
            if (confirmed == null) {
                return session.toStatus();
            }
            return confirmPreview(session.getSessionId(), confirmed);
        }
        if (AssistantVariables.StageAwaitInstall.equals(stage)) {
            Boolean accepted = parseYesNo(userText);
            if (accepted == null) {
                return session.toStatus();
            }
            return confirmInstall(session.getSessionId(), accepted);
        }
        return null;
    }

    public void clearByTarget(String targetProcessId) {
        WriteWorkflowSession old = sessionsByTarget.remove(targetProcessId);
        if (old != null) {
            sessionsById.remove(old.getSessionId());
        }
    }

    private void put(WriteWorkflowSession session) {
        if (AssistantVariables.StageDone.equals(session.getStage())) {
            session.setActive(false);
            // 完成后不再按目标保留，避免前端轮询一直拉出确认/完成卡片
            clearByTarget(session.getTargetProcessId());
            sessionsById.remove(session.getSessionId());
            return;
        }
        sessionsByTarget.put(session.getTargetProcessId(), session);
        sessionsById.put(session.getSessionId(), session);
    }

    private WriteWorkflowSession requireSession(String sessionId) {
        WriteWorkflowSession session = sessionsById.get(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + sessionId);
        }
        return session;
    }

    private void ensureEnabled() {
        if (!isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AI 写工作流未启用");
        }
    }

    private static Boolean parseYesNo(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String t = text.trim().toLowerCase();
        if (t.equals("y") || t.equals("yes") || t.equals("是") || t.equals("确认") || t.equals("同意")
                || t.equals("接受") || t.contains("确认保存") || t.contains("确认安装")) {
            return true;
        }
        if (t.equals("n") || t.equals("no") || t.equals("否") || t.equals("拒绝") || t.equals("取消")
                || t.contains("不要") || t.contains("拒绝")) {
            return false;
        }
        return null;
    }
}
