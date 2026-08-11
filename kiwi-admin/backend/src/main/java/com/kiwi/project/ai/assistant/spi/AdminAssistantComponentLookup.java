package com.kiwi.project.ai.assistant.spi;

import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminAssistantComponentLookup implements AssistantComponentLookup {

    private final BpmComponentService bpmComponentService;
    private final BpmComponentPluginLoader bpmComponentPluginLoader;

    @Override
    public boolean exists(String componentId) {
        return StringUtils.isNotBlank(componentId)
                && bpmComponentService.resolveComponentById(componentId) != null;
    }

    @Override
    public List<String> requiredInputKeys(String componentId) {
        BpmComponent component = bpmComponentService.resolveComponentById(componentId);
        if (component == null || component.getInputParameters() == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (BpmComponentParameter p : component.getInputParameters()) {
            if (p != null && p.isRequired() && StringUtils.isNotBlank(p.getKey())) {
                keys.add(p.getKey());
            }
        }
        return keys;
    }

    @Override
    public Optional<String> pluginMissingHint(String componentId) {
        if (StringUtils.isBlank(componentId) || !componentId.startsWith("plugin_")) {
            return Optional.empty();
        }
        if (exists(componentId)) {
            return Optional.empty();
        }
        Map<String, String> jarIndex = bpmComponentPluginLoader.buildPluginJarIndex();
        if (jarIndex.containsKey(componentId)) {
            return Optional.empty();
        }
        return Optional.of(componentId);
    }
}
