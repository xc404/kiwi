package com.kiwi.project.ai.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.assistant.AssistantCatalog;
import com.kiwi.bpmn.assistant.AssistantProperties;
import com.kiwi.bpmn.assistant.spi.AssistantBpmnLookup;
import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AssistantCatalogContextBuilder {

    private final AssistantProperties assistantProperties;
    private final BpmComponentService bpmComponentService;
    private final BpmComponentDao bpmComponentDao;
    private final AssistantBpmnLookup bpmnLookup;
    private final BpmComponentPluginLoader bpmComponentPluginLoader;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<BpmRemoteMarketService> remoteMarketServiceProvider;

    public AssistantCatalog build(String scenario, List<String> keywords) {
        AssistantProperties cfg = assistantProperties;
        int installedTop = Math.max(1, cfg.getCatalogInstalledTopN());
        int templateTop = Math.max(1, cfg.getCatalogTemplateTopN());
        int installableTop = Math.max(1, cfg.getCatalogInstallableTopN());

        List<String> kws = keywords != null ? keywords : List.of();
        AssistantCatalog catalog = new AssistantCatalog();

        // 直接回源 DAO，避免仅依赖内存缓存（Job 线程/启动竞态下曾出现 installed 为空）
        List<BpmComponent> all = bpmComponentDao.findAll();
        if (all.isEmpty()) {
            all = bpmComponentService.listAllComponents();
        } else {
            bpmComponentService.refresh();
        }
        log.info("AI authoring catalog source size={}, keywords={}", all.size(), kws);
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
                List<String> componentIds = item.getComponentKeys() == null || item.getComponentKeys().isEmpty()
                        ? List.of(StringUtils.defaultIfBlank(item.getSlug(), item.getName()))
                        : item.getComponentKeys();
                for (String componentId : componentIds) {
                    if (catalog.getInstallable().size() >= installableTop) {
                        break;
                    }
                    if (StringUtils.isBlank(componentId)) {
                        continue;
                    }
                    catalog.getInstallable().add(toInstallable(componentId, item));
                }
            }
        }

        try {
            for (AssistantBpmnLookup.TemplateSummary summary :
                    bpmnLookup.findMatureTemplates(scenario, kws, templateTop)) {
                AssistantCatalog.CatalogTemplate t = new AssistantCatalog.CatalogTemplate();
                t.setPackId(summary.getPackId());
                t.setName(summary.getName());
                t.setSummary(summary.getSummary());
                if (summary.getTags() != null) {
                    t.setTags(new ArrayList<>(summary.getTags()));
                }
                t.setReferenceProcessKey(summary.getReferenceProcessKey());
                t.setReferenceBpmnXml(summary.getReferenceBpmnXml());
                catalog.getTemplates().add(t);
            }
        } catch (Exception e) {
            // 模板检索失败不应让整次 Catalog 变空（否则 LLM 会认为「无可用组件」）
            log.warn("AI authoring 模板检索失败，继续使用已装组件 Catalog: {}", e.toString());
        }
        return catalog;
    }

    private AssistantCatalog.CatalogComponent toInstallable(
            String componentId, BpmRemoteMarketItemDto item) {
        AssistantCatalog.CatalogComponent entry = new AssistantCatalog.CatalogComponent();
        entry.setId(componentId);
        entry.setName(item.getName());
        entry.setDescription(truncate(item.getSummary(), 500));
        entry.setSource("remote-plugin");
        entry.setStatus("available_to_install");
        entry.setRequiresInstall(true);
        entry.setPluginHint(item.getDownloadUrl() != null ? item.getDownloadUrl() : item.getSlug());
        entry.setMarketSlug(item.getSlug());
        entry.setMarketVersion(item.getVersion());
        entry.setMarketSourceId(item.getSourceId());
        return entry;
    }

    public String buildAsJson(String scenario, List<String> keywords) {
        AssistantCatalog catalog = build(scenario, keywords);
        try {
            String json = objectMapper.writeValueAsString(slimForProcessVariable(catalog));
            // H2 / Operaton 历史变量 TEXT_ 上限 4000；预留余量给 Unicode 转义
            int guard = 0;
            while (json.length() > 3500 && guard++ < 8) {
                if (!catalog.getInstalled().isEmpty()) {
                    catalog.getInstalled().remove(catalog.getInstalled().size() - 1);
                } else if (!catalog.getInstallable().isEmpty()) {
                    catalog.getInstallable().remove(catalog.getInstallable().size() - 1);
                } else {
                    break;
                }
                json = objectMapper.writeValueAsString(slimForProcessVariable(catalog));
            }
            log.info("AI authoring catalog json bytes={}, installed={}, installable={}, templates={}",
                    json.length(),
                    catalog.getInstalled().size(),
                    catalog.getInstallable().size(),
                    catalog.getTemplates().size());
            return json;
        } catch (Exception e) {
            log.error("AI authoring catalog 序列化失败: {}", e.toString(), e);
            return "{\"installed\":[],\"installable\":[],\"templates\":[]}";
        }
    }

    private AssistantCatalog slimForProcessVariable(AssistantCatalog source) {
        AssistantCatalog slim = new AssistantCatalog();
        for (AssistantCatalog.CatalogComponent c : source.getInstalled()) {
            AssistantCatalog.CatalogComponent copy = new AssistantCatalog.CatalogComponent();
            copy.setId(c.getId());
            copy.setName(truncate(c.getName(), 40));
            copy.setDescription(truncate(c.getDescription(), 80));
            copy.setDelegateExpression(c.getDelegateExpression());
            copy.setStatus(c.getStatus());
            copy.setSource(c.getSource());
            copy.setGroup(truncate(c.getGroup(), 20));
            copy.setRequiresInstall(c.isRequiresInstall());
            if (c.getInputs() != null) {
                c.getInputs().stream()
                        .filter(p -> p != null && p.isRequired())
                        .limit(4)
                        .forEach(p -> {
                            AssistantCatalog.CatalogParameter pc = new AssistantCatalog.CatalogParameter();
                            pc.setKey(p.getKey());
                            pc.setRequired(true);
                            pc.setType(p.getType());
                            pc.setExample(truncate(p.getExample(), 40));
                            copy.getInputs().add(pc);
                        });
            }
            slim.getInstalled().add(copy);
        }
        for (AssistantCatalog.CatalogComponent c : source.getInstallable()) {
            AssistantCatalog.CatalogComponent copy = new AssistantCatalog.CatalogComponent();
            copy.setId(c.getId());
            copy.setName(truncate(c.getName(), 40));
            copy.setStatus(c.getStatus());
            copy.setRequiresInstall(true);
            copy.setMarketSlug(c.getMarketSlug());
            copy.setMarketVersion(c.getMarketVersion());
            copy.setMarketSourceId(c.getMarketSourceId());
            slim.getInstallable().add(copy);
        }
        for (AssistantCatalog.CatalogTemplate t : source.getTemplates()) {
            AssistantCatalog.CatalogTemplate copy = new AssistantCatalog.CatalogTemplate();
            copy.setPackId(t.getPackId());
            copy.setName(truncate(t.getName(), 40));
            copy.setSummary(truncate(t.getSummary(), 80));
            copy.setReferenceProcessKey(t.getReferenceProcessKey());
            // 不把 referenceBpmnXml 写入流程变量，避免撑爆 TEXT_ 列
            slim.getTemplates().add(copy);
        }
        return slim;
    }

    private AssistantCatalog.CatalogComponent toInstalled(BpmComponent c, Map<String, String> jarIndex) {
        BpmComponent filled = bpmComponentService.fillComponentProperties(c);
        BpmComponent effective = filled != null ? filled : c;
        AssistantCatalog.CatalogComponent e = new AssistantCatalog.CatalogComponent();
        e.setId(effective.getId());
        e.setName(effective.getName());
        e.setDescription(truncate(effective.getDescription(), 500));
        e.setSource(StringUtils.defaultIfBlank(effective.getSource(), c.getSource()));
        e.setGroup(effective.getGroup());
        if (StringUtils.isNotBlank(effective.getKey())) {
            e.setDelegateExpression("${" + effective.getKey() + "}");
        }
        if (effective.getInputParameters() != null) {
            effective.getInputParameters().stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(input -> !input.isHidden())
                    .sorted(Comparator
                            .comparing(BpmComponentParameter::isRequired).reversed()
                            .thenComparing(Comparator
                                    .comparing(BpmComponentParameter::isImportant).reversed()))
                    .limit(12)
                    .map(this::toCatalogParameter)
                    .forEach(e.getInputs()::add);
        }
        e.setStatus("installed");
        e.setRequiresInstall(false);
        e.setPluginHint(jarIndex.get(effective.getId()));
        return e;
    }

    private AssistantCatalog.CatalogParameter toCatalogParameter(BpmComponentParameter input) {
        AssistantCatalog.CatalogParameter parameter = new AssistantCatalog.CatalogParameter();
        parameter.setKey(input.getKey());
        parameter.setDescription(truncate(input.getDescription(), 300));
        parameter.setType(input.getType());
        parameter.setDefaultValue(truncate(input.getDefaultValue(), 200));
        parameter.setExample(truncate(input.getExample(), 200));
        parameter.setRequired(input.isRequired());
        return parameter;
    }

    private String truncate(String value, int maxLength) {
        if (StringUtils.isBlank(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
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
