package com.kiwi.project.bpm.service;

import com.kiwi.project.bpm.dao.BpmInboundRegistrationDao;
import com.kiwi.project.bpm.model.BpmInboundRegistration;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.MismatchingMessageCorrelationException;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.RuntimeService;
import org.operaton.bpm.engine.runtime.MessageCorrelationBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class BpmInboundCorrelationService {

    private final BpmInboundRegistrationDao inboundRegistrationDao;
    private final RuntimeService runtimeService;

    public BpmInboundCorrelationService(BpmInboundRegistrationDao inboundRegistrationDao, ProcessEngine processEngine) {
        this.inboundRegistrationDao = inboundRegistrationDao;
        this.runtimeService = processEngine.getRuntimeService();
    }

    public int correlate(String componentKey, String inboundToken, Map<String, Object> variables) {
        BpmInboundRegistration reg =
                inboundRegistrationDao
                        .findByComponentKey(componentKey)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "未注册的入站组件: " + componentKey));

        if (Boolean.FALSE.equals(reg.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.GONE, "入站组件已禁用: " + componentKey);
        }
        if (StringUtils.isNotBlank(reg.getSecretToken())) {
            if (!reg.getSecretToken().equals(inboundToken)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "入站 Token 无效");
            }
        }
        if (StringUtils.isBlank(reg.getMessageName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "注册未配置 messageName");
        }

        MessageCorrelationBuilder builder =
                runtimeService.createMessageCorrelation(reg.getMessageName());
        if (StringUtils.isNotBlank(reg.getProjectId())) {
            builder.processInstanceVariableEquals("projectId", reg.getProjectId());
        }
        if (variables != null && !variables.isEmpty()) {
            builder.setVariables(variables);
        }
        try {
            return builder.correlateAllWithResult().size();
        } catch (MismatchingMessageCorrelationException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "无等待中的 Message 订阅: " + reg.getMessageName(), e);
        }
    }
}
