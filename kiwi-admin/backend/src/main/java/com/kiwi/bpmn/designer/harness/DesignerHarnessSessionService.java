package com.kiwi.bpmn.designer.harness;

import com.kiwi.bpmn.designer.harness.model.HarnessChatMessage;
import com.kiwi.bpmn.designer.harness.persist.DesignerHarnessSession;
import com.kiwi.bpmn.designer.harness.persist.DesignerHarnessSessionDao;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmOwnershipAccessService;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DesignerHarnessSessionService {

    private final DesignerHarnessSessionDao sessionDao;
    private final BpmProcessDefinitionDao processDefinitionDao;
    private final BpmProcessDefinitionService processDefinitionService;
    private final BpmOwnershipAccessService ownershipAccessService;
    private final DesignerHarnessTurnLlm turnLlm;
    private final DesignerHarnessTurnContext turnContext;
    private final AiChatProperties aiChatProperties;

    public DesignerHarnessConfig config() {
        DesignerHarnessConfig config = new DesignerHarnessConfig();
        config.setEnabled(aiChatProperties.isEnabled());
        return config;
    }

    public DesignerHarnessStatus status(String targetProcessId, String userId) {
        BpmProcess process = requireProcess(targetProcessId, userId);
        DesignerHarnessSession session = sessionDao.findById(targetProcessId).orElse(null);
        return toStatus(session, process);
    }

    public DesignerHarnessStatus clear(String targetProcessId, String userId) {
        requireProcess(targetProcessId, userId);
        sessionDao.findById(targetProcessId).ifPresent(sessionDao::delete);
        BpmProcess process = processDefinitionDao.findById(targetProcessId).orElseThrow();
        return toStatus(null, process);
    }

    public DesignerHarnessStatus turn(DesignerHarnessTurnRequest request, String userId) {
        if (!aiChatProperties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI 未启用");
        }
        if (request == null || StringUtils.isBlank(request.getTargetProcessId()) || StringUtils.isBlank(request.getMessage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetProcessId 与 message 必填");
        }
        String processId = request.getTargetProcessId().trim();
        BpmProcess process = requireProcess(processId, userId);
        DesignerHarnessSession session = sessionDao.findById(processId).orElseGet(() -> newSession(processId, userId));
        if (session.isRunning()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "上一轮还在处理");
        }
        if (StringUtils.isNotBlank(request.getCanvasBpmnXml())
                && !StringUtils.equals(request.getCanvasBpmnXml().trim(), StringUtils.trimToEmpty(process.getBpmnXml()))) {
            process.setBpmnXml(request.getCanvasBpmnXml().trim());
            processDefinitionService.syncBpmnIdentity(process);
            processDefinitionDao.updateSelective(process);
        }
        append(session, "user", request.getMessage().trim());
        session.setSelectedElementId(request.getSelectedElementId());
        session.setRunning(true);
        session.setErrorMessage(null);
        sessionDao.save(session);
        String xml = processDefinitionDao.findById(processId).map(BpmProcess::getBpmnXml).orElse("");
        try {
            turnContext.bind(session, xml);
            String reply = turnLlm.complete(session, xml, request.getSelectedElementId());
            append(session, "assistant", reply);
        } catch (Exception e) {
            session.setErrorMessage(e.getMessage());
            append(session, "assistant", "本轮失败: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        } finally {
            session.setRunning(false);
            turnContext.clear();
            sessionDao.save(session);
        }
        BpmProcess latest = processDefinitionDao.findById(processId).orElse(process);
        return toStatus(session, latest);
    }

    private DesignerHarnessSession newSession(String processId, String userId) {
        DesignerHarnessSession session = new DesignerHarnessSession();
        session.setId(processId);
        session.setInitiatorUserId(userId);
        session.setMessages(new ArrayList<>());
        return sessionDao.save(session);
    }

    private void append(DesignerHarnessSession session, String role, String text) {
        if (session.getMessages() == null) {
            session.setMessages(new ArrayList<>());
        }
        HarnessChatMessage message = new HarnessChatMessage();
        message.setId(UUID.randomUUID().toString());
        message.setRole(role);
        message.setText(text);
        message.setCreatedAt(new Date());
        session.getMessages().add(message);
    }

    private BpmProcess requireProcess(String processId, String userId) {
        ownershipAccessService.assertOwnsProcess(userId, processId);
        return processDefinitionDao.findById(processId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "流程不存在"));
    }

    private DesignerHarnessStatus toStatus(DesignerHarnessSession session, BpmProcess process) {
        DesignerHarnessStatus status = new DesignerHarnessStatus();
        status.setTargetProcessId(process.getId());
        status.setBpmnXml(process.getBpmnXml());
        if (session == null) {
            status.setRunning(false);
            status.setInputEnabled(true);
            status.setMessages(List.of());
            return status;
        }
        status.setRunning(session.isRunning());
        status.setInputEnabled(!session.isRunning());
        status.setErrorMessage(session.getErrorMessage());
        status.setMessages(session.getMessages() == null ? List.of() : session.getMessages());
        return status;
    }
}