package com.kiwi.project.ai.authoring;

import com.kiwi.project.ai.AiChatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.repository.Deployment;
import org.operaton.bpm.engine.repository.ProcessDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "kiwi.ai.workflow-authoring", name = "enabled", havingValue = "true")
public class AiAuthoringProcessDeployer {

    private final ProcessEngine processEngine;
    private final AiChatProperties aiChatProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void deployIfMissing() {
        String key = aiChatProperties.getWorkflowAuthoring().getProcessDefinitionKey();
        ProcessDefinition existing = processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionKey(key)
                .latestVersion()
                .singleResult();
        if (existing != null) {
            log.info("AI authoring process already deployed: {} v{}", key, existing.getVersion());
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource("bpm/ai/kiwi_ai_workflow_authoring.bpmn");
            String xml = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            Deployment deployment = processEngine.getRepositoryService()
                    .createDeployment()
                    .name("kiwi-ai-workflow-authoring")
                    .addString(key + ".bpmn", xml)
                    .enableDuplicateFiltering(true)
                    .deploy();
            log.info("Deployed AI authoring process {} deploymentId={}", key, deployment.getId());
        } catch (Exception e) {
            log.error("Failed to deploy AI authoring process {}", key, e);
        }
    }
}
