package com.kiwi.project.ai.assistant.spi;

import com.kiwi.bpmn.assistant.AssistantComponentIdAliases;
import com.kiwi.bpmn.assistant.spi.AssistantComponentLookup;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminAssistantComponentLookup implements AssistantComponentLookup {

    private final BpmComponentService bpmComponentService;
    private final BpmComponentPluginLoader bpmComponentPluginLoader;
    private final ObjectProvider<BpmRemoteMarketService> remoteMarketServiceProvider;

    @Override
    public boolean exists(String componentId) {
        return resolveComponent(componentId) != null;
    }

    @Override
    public List<String> requiredInputKeys(String componentId) {
        BpmComponent component = resolveComponent(componentId);
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
    public Optional<String> resolveDelegateExpression(String componentId) {
        BpmComponent component = resolveComponent(componentId);
        if (component == null) {
            return Optional.empty();
        }
        if (StringUtils.isNotBlank(component.getKey())) {
            return Optional.of("${" + component.getKey() + "}");
        }
        return Optional.of("${" + AssistantComponentIdAliases.beanName(component.getId()) + "}");
    }

    @Override
    public Optional<String> pluginMissingHint(String componentId) {
        if (StringUtils.isBlank(componentId) || exists(componentId)) {
            return Optional.empty();
        }
        Map<String, String> jarIndex = bpmComponentPluginLoader.buildPluginJarIndex();
        for (String id : AssistantComponentIdAliases.idAndAlternate(componentId)) {
            if (jarIndex.containsKey(id)) {
                return Optional.empty();
            }
        }
        BpmRemoteMarketService remote = remoteMarketServiceProvider.getIfAvailable();
        if (remote != null && remote.isEnabled()) {
            String keyword = AssistantComponentIdAliases.beanName(componentId);
            List<BpmRemoteMarketItemDto> plugins = remote.listItems("plugin", keyword, null);
            for (BpmRemoteMarketItemDto item : plugins) {
                if (itemMatchesComponent(item, componentId)) {
                    return Optional.of(encodeMarketHint(item, componentId));
                }
            }
            // 宽搜：无 keyword 时再扫一轮（条目通常不多）
            if (StringUtils.isNotBlank(keyword)) {
                for (BpmRemoteMarketItemDto item : remote.listItems("plugin", null, null)) {
                    if (itemMatchesComponent(item, componentId)) {
                        return Optional.of(encodeMarketHint(item, componentId));
                    }
                }
            }
        }
        if (componentId.startsWith("plugin_") || componentId.startsWith("classpath_")) {
            return Optional.of(componentId);
        }
        return Optional.empty();
    }

    private BpmComponent resolveComponent(String componentId) {
        if (StringUtils.isBlank(componentId)) {
            return null;
        }
        return bpmComponentService.resolveComponentById(componentId);
    }

    private boolean itemMatchesComponent(BpmRemoteMarketItemDto item, String componentId) {
        if (item == null || StringUtils.isBlank(componentId)) {
            return false;
        }
        List<String> candidates = AssistantComponentIdAliases.idAndAlternate(componentId);
        if (item.getComponentKeys() != null) {
            for (String key : item.getComponentKeys()) {
                for (String candidate : candidates) {
                    if (candidate.equals(key)) {
                        return true;
                    }
                }
            }
        }
        String slug = StringUtils.defaultString(item.getSlug()).toLowerCase(Locale.ROOT);
        String bean = AssistantComponentIdAliases.beanName(componentId).toLowerCase(Locale.ROOT);
        return StringUtils.isNotBlank(bean) && (slug.equals(bean) || slug.contains(bean));
    }

    /**
     * 紧凑 hint，供 install 解析：slug|version|sourceId|componentId
     */
    public static String encodeMarketHint(BpmRemoteMarketItemDto item, String componentId) {
        return String.join("|",
                StringUtils.defaultString(item.getSlug()),
                StringUtils.defaultString(item.getVersion()),
                StringUtils.defaultString(item.getSourceId()),
                StringUtils.defaultString(componentId));
    }

    public static Optional<MarketHint> parseMarketHint(String hint) {
        if (StringUtils.isBlank(hint) || !hint.contains("|")) {
            return Optional.empty();
        }
        String[] parts = hint.split("\\|", -1);
        if (parts.length < 4 || StringUtils.isAnyBlank(parts[0], parts[1])) {
            return Optional.empty();
        }
        return Optional.of(new MarketHint(parts[0], parts[1],
                StringUtils.isBlank(parts[2]) ? null : parts[2], parts[3]));
    }

    public record MarketHint(String slug, String version, String sourceId, String componentId) {
    }
}
