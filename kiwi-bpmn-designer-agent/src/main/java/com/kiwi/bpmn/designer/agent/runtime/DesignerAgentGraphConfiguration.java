package com.kiwi.bpmn.designer.agent.runtime;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Graph checkpoint 默认内存实现（admin 可在 mongodb 模式下覆盖 {@link BaseCheckpointSaver}）。
 */
@Configuration
public class DesignerAgentGraphConfiguration {

    @Bean
    @ConditionalOnMissingBean(BaseCheckpointSaver.class)
    public BaseCheckpointSaver designerAgentMemoryCheckpointSaver() {
        return MemorySaver.builder().build();
    }
}
