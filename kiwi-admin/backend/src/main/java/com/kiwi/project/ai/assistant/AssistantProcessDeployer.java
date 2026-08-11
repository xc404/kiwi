package com.kiwi.project.ai.assistant;

import com.kiwi.bpmn.assistant.AssistantProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.repository.Deployment;
import org.operaton.bpm.engine.repository.ProcessDefinition;
import org.operaton.bpm.engine.repository.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 部署 AI 写工作流元流程 BPMN（资源位于 admin classpath：{@code bpm/ai/kiwi_ai_workflow_authoring.bpmn}）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "kiwi.ai.workflow-authoring", name = "enabled", havingValue = "true")
public class AssistantProcessDeployer {

    private final ProcessEngine processEngine;
    private final AssistantProperties assistantProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void deployIfChanged() {
        String key = assistantProperties.getProcessDefinitionKey();
        String resourceName = key + ".bpmn";
        try {
            ClassPathResource resource = new ClassPathResource("bpm/ai/kiwi_ai_workflow_authoring.bpmn");
            byte[] classpathBytes = StreamUtils.copyToByteArray(resource.getInputStream());
            ProcessDefinition existing = processEngine.getRepositoryService()
                    .createProcessDefinitionQuery()
                    .processDefinitionKey(key)
                    .latestVersion()
                    .singleResult();
            if (existing != null && sameDeployedBpmn(existing.getDeploymentId(), classpathBytes)) {
                log.info("AI assistant process unchanged: {} v{}", key, existing.getVersion());
                return;
            }
            String xml = new String(classpathBytes, StandardCharsets.UTF_8);
            Deployment deployment = processEngine.getRepositoryService()
                    .createDeployment()
                    .name("kiwi-ai-workflow-authoring")
                    .addString(resourceName, xml)
                    .deploy();
            log.info("Deployed AI assistant process {} v{} deploymentId={}",
                    key,
                    processEngine.getRepositoryService()
                            .createProcessDefinitionQuery()
                            .processDefinitionKey(key)
                            .latestVersion()
                            .singleResult()
                            .getVersion(),
                    deployment.getId());
        } catch (Exception e) {
            log.error("Failed to deploy AI assistant process {}", key, e);
        }
    }

    private boolean sameDeployedBpmn(String deploymentId, byte[] classpathBytes) {
        try {
            List<Resource> resources = processEngine.getRepositoryService().getDeploymentResources(deploymentId);
            if (resources == null || resources.isEmpty()) {
                return false;
            }
            for (Resource resource : resources) {
                String name = resource.getName();
                if (name == null || !(name.endsWith(".bpmn") || name.endsWith(".bpmn20.xml"))) {
                    continue;
                }
                try (InputStream in = processEngine.getRepositoryService()
                        .getResourceAsStream(deploymentId, name)) {
                    if (in != null && Arrays.equals(classpathBytes, StreamUtils.copyToByteArray(in))) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Cannot compare AI assistant deployment {}, will redeploy: {}",
                    deploymentId, e.getMessage());
            return false;
        }
    }
}
