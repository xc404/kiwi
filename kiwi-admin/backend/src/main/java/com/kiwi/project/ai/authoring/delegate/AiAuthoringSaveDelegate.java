package com.kiwi.project.ai.authoring.delegate;

import com.kiwi.project.ai.authoring.AiAuthoringVariables;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmProcess;
import com.kiwi.project.bpm.service.BpmProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component("aiAuthoringSaveDelegate")
@RequiredArgsConstructor
public class AiAuthoringSaveDelegate implements JavaDelegate {

    private final BpmProcessDefinitionDao bpmProcessDefinitionDao;
    private final BpmProcessDefinitionService bpmProcessDefinitionService;

    @Override
    public void execute(DelegateExecution execution) {
        Object confirmed = execution.getVariable(AiAuthoringVariables.PreviewConfirmed);
        boolean ok = Boolean.TRUE.equals(confirmed) || "true".equalsIgnoreCase(String.valueOf(confirmed));
        if (!ok) {
            execution.setVariable(AiAuthoringVariables.ErrorMessage, "预览未确认，跳过保存");
            return;
        }
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageSave);
        String targetId = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.TargetProcessId);
        String xml = AiAuthoringExtractDelegate.str(execution, AiAuthoringVariables.CandidateXml);
        if (StringUtils.isBlank(targetId) || StringUtils.isBlank(xml)) {
            execution.setVariable(AiAuthoringVariables.ErrorMessage, "targetProcessId 或 candidateXml 为空");
            return;
        }
        BpmProcess process = bpmProcessDefinitionDao.findById(targetId)
                .orElseThrow(() -> new IllegalStateException("目标流程不存在: " + targetId));
        process.setBpmnXml(xml);
        bpmProcessDefinitionService.syncBpmnIdentity(process);
        process.setUpdatedTime(new Date());
        bpmProcessDefinitionDao.save(process);
        execution.setVariable(AiAuthoringVariables.Stage, AiAuthoringVariables.StageDone);
    }
}
