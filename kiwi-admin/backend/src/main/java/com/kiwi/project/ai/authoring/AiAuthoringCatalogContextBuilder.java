package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import com.kiwi.project.bpm.service.BpmTemplatePackService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AiAuthoringCatalogContextBuilder {

    private final AiChatProperties aiChatProperties;
    private final BpmComponentService bpmComponentService;
    private final BpmTemplatePackService bpmTemplatePackService;
    private final BpmComponentPluginLoader bpmComponentPluginLoader;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<BpmRemoteMarketService> remoteMarketServiceProvider;

    public AiAuthoringCatalog build(String scenario, List<String> keywords) {
        AiChatProperties.WorkflowAuthoring cfg = aiChatProperties.getWorkflowAuthoring();
        int installedTop = Math.max(1, cfg.getCatalogInstalledTopN());
        int templateTop = Math.max(1, cfg.getCatalogTemplateTopN());
        int installableTop = Math.max(1, cfg.getCatalogInstallableTopN());

        List<String> kws = keywords != null ? keywords : List.of();
        AiAuthoringCatalog catalog = new AiAuthoringCatalog();

        List<BpmComponent> all = bpmComponentService.listCachedComponents();
        Map<String, String> pluginJarIndex = bpmComponentPluginLoader.buildPluginJarIndex();

        List<Scored<BpmComponent>> scored = all.stream()
                .map(c -> new Scored<>(c, scoreComponent(c, kws)))
                .sorted(Comparator.comparingInt((Scored<BpmComponent> s) -> s.score).reversed())
                .toList();

        for (Scored<BpmComponent> s : scored) {
            if (catalog.getInstalled().size() >= installedTop) {
                break;
            }
            if (s.score <= 0 && catalog.getInstalled().size() >= Math.min(12, installedTop)) {
                continue;
            }
            catalog.getInstalled().add(toInstalled(s.value, pluginJarIndex));
        }
        if (catalog.getInstalled().isEmpty()) {
            scored.stream().limit(Math.min(12, installedTop)).forEach(s ->
                    catalog.getInstalled().add(toInstalled(s.value, pluginJarIndex)));
        }

        BpmRemoteMarketService remote = remoteMarketServiceProvider.getIfAvailable();
        if (remote != null && remote.isEnabled()) {
            String kw = kws.isEmpty() ? "" : kws.get(0);
            List<BpmRemoteMarketItemDto> plugins = remote.listItems("plugin", kw, null);
            for (BpmRemoteMarketItemDto item : plugins) {
                if (catalog.getInstallable().size() >= installableTop) {
                    break;
                }
                AiAuthoringCatalog.CatalogComponent e = new AiAuthoringCatalog.CatalogComponent();
                e.setId(item.getSlug() != null ? item.getSlug() : item.getName());
                e.setName(item.getName());
                e.setSource("remote-plugin");
                e.setStatus("available_to_install");
                e.setRequiresInstall(true);
                e.setPluginHint(item.getDownloadUrl() != null ? item.getDownloadUrl() : item.getSlug());
                catalog.getInstallable().add(e);
            }
        }

        String userId = safeUserId();
        BpmTemplatePackService.PackQueryInput q = new BpmTemplatePackService.PackQueryInput();
        if (!kws.isEmpty()) {
            q.setKeyword(kws.get(0));
        }
        var page = bpmTemplatePackService.page(q, PageRequest.of(0, templateTop), userId);
        for (BpmTemplatePack pack : page.getContent()) {
            AiAuthoringCatalog.CatalogTemplate t = new AiAuthoringCatalog.CatalogTemplate();
            t.setPackId(pack.getId());
            t.setName(pack.getName());
            t.setSummary(pack.getSummary());
            if (pack.getTags() != null) {
                t.setTags(new ArrayList<>(pack.getTags()));
            }
            catalog.getTemplates().add(t);
        }
        return catalog;
    }

    public String buildAsJson(String scenario, List<String> keywords) {
        try {
            return objectMapper.writeValueAsString(build(scenario, keywords));
        } catch (Exception e) {
            return "{\"installed\":[],\"installable\":[],\"templates\":[]}";
        }
    }

    private String safeUserId() {
        try {
            if (sessionService.getCurrentUser() != null) {
                return sessionService.getCurrentUser().getId();
            }
        } catch (Exception ignored) {
            // no session
        }
        return null;
    }

    private AiAuthoringCatalog.CatalogComponent toInstalled(BpmComponent c, Map<String, String> jarIndex) {
        AiAuthoringCatalog.CatalogComponent e = new AiAuthoringCatalog.CatalogComponent();
        e.setId(c.getId());
        e.setName(c.getName());
        e.setSource(c.getSource());
        e.setGroup(c.getGroup());
        e.setStatus("installed");
        e.setRequiresInstall(false);
        e.setPluginHint(jarIndex.get(c.getId()));
        return e;
    }

    private int scoreComponent(BpmComponent c, List<String> keywords) {
        int score = 0;
        String hay = (nullToEmpty(c.getId()) + " " + nullToEmpty(c.getName()) + " "
                + nullToEmpty(c.getKey()) + " " + nullToEmpty(c.getGroup()) + " "
                + nullToEmpty(c.getDescription())).toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (StringUtils.isNotBlank(kw) && hay.contains(kw.toLowerCase(Locale.ROOT))) {
                score += 10;
            }
        }
        if ("classpath".equals(c.getSource())) {
            score += 1;
        }
        return score;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record Scored<T>(T value, int score) {
    }
}
