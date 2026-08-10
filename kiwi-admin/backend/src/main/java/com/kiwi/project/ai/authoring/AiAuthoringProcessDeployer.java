package com.kiwi.project.ai.authoring;

import com.kiwi.project.ai.AiChatProperties;
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

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "kiwi.ai.workflow-authoring", name = "enabled", havingValue = "true")
public class AiAuthoringProcessDeployer {

    private final ProcessEngine processEngine;
    private final AiChatProperties aiChatProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void deployIfChanged() {
        String key = aiChatProperties.getWorkflowAuthoring().getProcessDefinitionKey();
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
                log.info("AI authoring process unchanged: {} v{}", key, existing.getVersion());
                return;
            }
            String xml = new String(classpathBytes, StandardCharsets.UTF_8);
            Deployment deployment = processEngine.getRepositoryService()
                    .createDeployment()
                    .name("kiwi-ai-workflow-authoring")
                    .addString(resourceName, xml)
                    .deploy();
            log.info("Deployed AI authoring process {} v{} deploymentId={}",
                    key,
                    processEngine.getRepositoryService()
                            .createProcessDefinitionQuery()
                            .processDefinitionKey(key)
                            .latestVersion()
                            .singleResult()
                            .getVersion(),
                    deployment.getId());
        } catch (Exception e) {
            log.error("Failed to deploy AI authoring process {}", key, e);
        }
    }

    /**
     * 比较 classpath BPMN 与已部署内容。旧部署资源名可能不是 {@code key.bpmn}，
     * Operaton 对缺失资源名会抛异常而非返回 null，因此先列资源再读。
     */
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
            log.warn("Cannot compare AI authoring deployment {}, will redeploy: {}",
                    deploymentId, e.getMessage());
            return false;
        }
    }
}
