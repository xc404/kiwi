package com.kiwi.project.ai.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.ai.AiChatProperties;
import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmComponentParameter;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import com.kiwi.project.bpm.service.BpmComponentPluginLoader;
import com.kiwi.project.bpm.service.BpmComponentService;
import com.kiwi.project.bpm.service.BpmRemoteMarketService;
import com.kiwi.project.bpm.service.BpmTemplatePackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AiAuthoringCatalogContextBuilder {

    private final AiChatProperties aiChatProperties;
    private final BpmComponentService bpmComponentService;
    private final BpmComponentDao bpmComponentDao;
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

        String userId = safeUserId();
        try {
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
                if (catalog.getTemplates().isEmpty()) {
                    attachReferenceBpmn(t, pack.getId(), userId);
                }
                catalog.getTemplates().add(t);
            }
        } catch (Exception e) {
            // 模板检索失败不应让整次 Catalog 变空（否则 LLM 会认为「无可用组件」）
            log.warn("AI authoring 模板检索失败，继续使用已装组件 Catalog: {}", e.toString());
        }
        return catalog;
    }

    private AiAuthoringCatalog.CatalogComponent toInstallable(
            String componentId, BpmRemoteMarketItemDto item) {
        AiAuthoringCatalog.CatalogComponent entry = new AiAuthoringCatalog.CatalogComponent();
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

    private void attachReferenceBpmn(
            AiAuthoringCatalog.CatalogTemplate target, String packId, String userId) {
        try {
            List<BpmTemplateProcess> processes = bpmTemplatePackService.listProcesses(packId, userId);
            BpmTemplateProcess reference = processes.stream()
                    .filter(BpmTemplateProcess::isEntry)
                    .findFirst()
                    .orElse(processes.isEmpty() ? null : processes.get(0));
            if (reference == null || StringUtils.isBlank(reference.getBpmnXml())) {
                return;
            }
            target.setReferenceProcessKey(reference.getProcessKey());
            target.setReferenceBpmnXml(truncate(reference.getBpmnXml(), 16_000));
        } catch (RuntimeException ignored) {
            // Catalog 摘要仍可用；参考模板读取失败不阻断生成。
        }
    }

    public String buildAsJson(String scenario, List<String> keywords) {
        AiAuthoringCatalog catalog = build(scenario, keywords);
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

    private AiAuthoringCatalog slimForProcessVariable(AiAuthoringCatalog source) {
        AiAuthoringCatalog slim = new AiAuthoringCatalog();
        for (AiAuthoringCatalog.CatalogComponent c : source.getInstalled()) {
            AiAuthoringCatalog.CatalogComponent copy = new AiAuthoringCatalog.CatalogComponent();
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
                            AiAuthoringCatalog.CatalogParameter pc = new AiAuthoringCatalog.CatalogParameter();
                            pc.setKey(p.getKey());
                            pc.setRequired(true);
                            pc.setType(p.getType());
                            pc.setExample(truncate(p.getExample(), 40));
                            copy.getInputs().add(pc);
                        });
            }
            slim.getInstalled().add(copy);
        }
        for (AiAuthoringCatalog.CatalogComponent c : source.getInstallable()) {
            AiAuthoringCatalog.CatalogComponent copy = new AiAuthoringCatalog.CatalogComponent();
            copy.setId(c.getId());
            copy.setName(truncate(c.getName(), 40));
            copy.setStatus(c.getStatus());
            copy.setRequiresInstall(true);
            copy.setMarketSlug(c.getMarketSlug());
            copy.setMarketVersion(c.getMarketVersion());
            copy.setMarketSourceId(c.getMarketSourceId());
            slim.getInstallable().add(copy);
        }
        for (AiAuthoringCatalog.CatalogTemplate t : source.getTemplates()) {
            AiAuthoringCatalog.CatalogTemplate copy = new AiAuthoringCatalog.CatalogTemplate();
            copy.setPackId(t.getPackId());
            copy.setName(truncate(t.getName(), 40));
            copy.setSummary(truncate(t.getSummary(), 80));
            copy.setReferenceProcessKey(t.getReferenceProcessKey());
            // 不把 referenceBpmnXml 写入流程变量，避免撑爆 TEXT_ 列
            slim.getTemplates().add(copy);
        }
        return slim;
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
        BpmComponent filled = bpmComponentService.fillComponentProperties(c);
        BpmComponent effective = filled != null ? filled : c;
        AiAuthoringCatalog.CatalogComponent e = new AiAuthoringCatalog.CatalogComponent();
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

    private AiAuthoringCatalog.CatalogParameter toCatalogParameter(BpmComponentParameter input) {
        AiAuthoringCatalog.CatalogParameter parameter = new AiAuthoringCatalog.CatalogParameter();
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
