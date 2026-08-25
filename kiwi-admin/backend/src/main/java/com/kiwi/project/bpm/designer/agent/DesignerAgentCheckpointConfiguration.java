package com.kiwi.project.bpm.designer.agent;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.kiwi.bpmn.designer.agent.DesignerAgentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class DesignerAgentCheckpointConfiguration {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "kiwi.bpm.designer-agent.checkpoint", havingValue = "mongodb", matchIfMissing = true)
    public BaseCheckpointSaver designerAgentMongoCheckpointSaver(MongoTemplate mongoTemplate) {
        return new DesignerAgentMongoCheckpointSaver(mongoTemplate);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "kiwi.bpm.designer-agent.checkpoint", havingValue = "memory")
    public BaseCheckpointSaver designerAgentExplicitMemoryCheckpointSaver() {
        return MemorySaver.builder().build();
    }
}
