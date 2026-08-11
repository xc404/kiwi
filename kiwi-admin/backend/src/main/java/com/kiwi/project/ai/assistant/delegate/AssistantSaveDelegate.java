package com.kiwi.project.ai.assistant.delegate;

import com.kiwi.bpmn.assistant.AssistantExecutionUtils;
import com.kiwi.bpmn.assistant.AssistantVariables;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component("bpmnAssistantSaveDelegate")
@RequiredArgsConstructor
public class AssistantSaveDelegate implements JavaDelegate {

    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final BpmProcessDefinitionService bpmProcessDefinitionService;

    @Override
    public void execute(DelegateExecution execution) {
        Object confirmed = execution.getVariable(AssistantVariables.PreviewConfirmed);
        boolean ok = Boolean.TRUE.equals(confirmed) || "true".equalsIgnoreCase(String.valueOf(confirmed));
        if (!ok) {
            execution.setVariable(AssistantVariables.ErrorMessage, "预览未确认，跳过保存");
            return;
        }
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageSave);
        String targetId = AssistantExecutionUtils.str(execution, AssistantVariables.TargetProcessId);
        String xml = AssistantExecutionUtils.str(execution, AssistantVariables.CandidateXml);
        if (StringUtils.isBlank(targetId) || StringUtils.isBlank(xml)) {
            execution.setVariable(AssistantVariables.ErrorMessage, "targetProcessId 或 candidateXml 为空");
            return;
        }
        BpmProcess process = bpmProcessDefinitionDao.findById(targetId)
                .orElseThrow(() -> new IllegalStateException("目标流程不存在: " + targetId));
        process.setBpmnXml(xml);
        bpmProcessDefinitionService.syncBpmnIdentity(process);
        process.setUpdatedTime(new Date());
        bpmProcessDefinitionDao.save(process);
        execution.setVariable(AssistantVariables.Stage, AssistantVariables.StageDone);
    }
}
