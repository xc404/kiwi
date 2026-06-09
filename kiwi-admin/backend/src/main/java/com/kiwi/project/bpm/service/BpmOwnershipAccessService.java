package com.kiwi.project.bpm.service;

import cn.dev33.satoken.stp.StpUtil;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import lombok.RequiredArgsConstructor;
import org.operaton.bpm.engine.HistoryService;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.history.HistoricProcessInstance;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * BPM 资源所有权：流程定义以 {@link BpmProcess#getCreatedBy()} 为准；
 * 流程实例以其 {@link HistoricProcessInstance#getTenantId()}（部署时写入为流程创建者）为准。
 */
@Service
@RequiredArgsConstructor
public class BpmOwnershipAccessService {

    public static final String BpmAdminPermission = "bpm:admin";

    private final ProcessEngine processEngine;
    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;

    private HistoryService historyService() {
        return processEngine.getHistoryService();
    }

    public boolean isBpmAdmin() {
        try {
            return StpUtil.hasPermission(BpmAdminPermission);
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean ownsProcess(String userId, String bpmProcessId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(bpmProcessId)) {
            return false;
        }
        return bpmProcessDefinitionDao.findById(bpmProcessId.trim())
                .map(p -> userId.equals(p.getCreatedBy()))
                .orElse(false);
    }

    public boolean ownsInstance(String userId, String instanceId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(instanceId)) {
            return false;
        }
        HistoricProcessInstance hip = historyService().createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId.trim())
                .singleResult();
        return hip != null && userId.equals(hip.getTenantId());
    }

    public void assertOwnsProcess(String userId, String bpmProcessId) {
        if (isBpmAdmin()) {
            return;
        }
        if (!StringUtils.hasText(bpmProcessId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "process id is required");
        }
        BpmProcess process = bpmProcessDefinitionDao.findById(bpmProcessId.trim()).orElse(null);
        if (process == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "process not found");
        }
        if (!StringUtils.hasText(userId) || !userId.equals(process.getCreatedBy())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该流程");
        }
    }

    public void assertOwnsInstance(String userId, String instanceId) {
        if (isBpmAdmin()) {
            return;
        }
        if (!StringUtils.hasText(instanceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "instance id is required");
        }
        HistoricProcessInstance hip = historyService().createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId.trim())
                .singleResult();
        if (hip == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "process instance not found");
        }
        if (!StringUtils.hasText(userId) || !userId.equals(hip.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该流程实例");
        }
    }

    public List<String> ownedProcessKeys(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return bpmProcessDefinitionDao.findByCreatedBy(userId).stream()
                .map(BpmProcess::getId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
