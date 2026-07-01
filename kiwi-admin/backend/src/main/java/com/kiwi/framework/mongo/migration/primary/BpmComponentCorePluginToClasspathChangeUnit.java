package com.kiwi.framework.mongo.migration.primary;

import com.kiwi.project.bpm.dao.BpmComponentDao;
import com.kiwi.project.bpm.dao.BpmProcessDefinitionDao;
import com.kiwi.project.bpm.model.BpmComponent;
import com.kiwi.project.bpm.model.BpmProcess;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 官方核心组件回归 classpath：将 {@code kiwi-bpmn-component} 相关 {@code plugin_*} id 迁回 {@code classpath_*}。
 */
@ChangeUnit(id = "20250701-001-bpm-component-core-plugin-to-classpath", order = "003", author = "kiwi")
@Slf4j
@RequiredArgsConstructor
public class BpmComponentCorePluginToClasspathChangeUnit {

    private static final String ClasspathPrefix = "classpath_";
    private static final String PluginPrefix = "plugin_";

    /** {@link com.kiwi.bpmn.component} 模块 component-bundle.json 中的 key */
    private static final Set<String> CoreComponentKeys = Set.of(
            "httpRequest",
            "shell",
            "jdbcActivity",
            "mongoActivity",
            "jsonMapActivity",
            "assignmentActivity",
            "sleep",
            "uuidGenerate",
            "base64Codec",
            "digestHash",
            "fileRead",
            "fileWrite",
            "sftpTransfer",
            "emailSend",
            "webhookOutbound");

    private final BpmProcessDefinitionDao bpmProcessDao;
    private final BpmComponentDao bpmComponentDao;

    @Execution
    public void execute() {
        int processCount = migrateProcesses();
        int componentCount = migrateComponents();
        log.info(
                "BPM 核心组件 plugin→classpath 迁移完成: processesUpdated={} componentsUpdated={}",
                processCount,
                componentCount);
    }

    @RollbackExecution
    public void rollback() {
        log.warn("BPM 核心组件 plugin→classpath 迁移不支持自动回滚");
    }

    private int migrateProcesses() {
        List<BpmProcess> all = bpmProcessDao.findAll();
        int updated = 0;
        for (BpmProcess process : all) {
            String xml = process.getBpmnXml();
            if (StringUtils.isBlank(xml) || !xml.contains(PluginPrefix)) {
                continue;
            }
            String migrated = migratePluginIdsInText(xml);
            if (!xml.equals(migrated)) {
                process.setBpmnXml(migrated);
                bpmProcessDao.save(process);
                updated++;
            }
        }
        return updated;
    }

    private int migrateComponents() {
        List<BpmComponent> all = bpmComponentDao.findAll();
        int updated = 0;
        for (BpmComponent component : all) {
            boolean changed = false;
            if (shouldMigrateId(component.getId())) {
                String newId = toClasspathId(component.getId());
                if (!newId.equals(component.getId())) {
                    component.setId(newId);
                    changed = true;
                }
            }
            if (shouldMigrateId(component.getParentId())) {
                String newParent = toClasspathId(component.getParentId());
                if (!newParent.equals(component.getParentId())) {
                    component.setParentId(newParent);
                    changed = true;
                }
            }
            if ("plugin".equals(component.getSource()) && isCoreComponent(component)) {
                component.setSource("classpath");
                changed = true;
            }
            if (changed) {
                bpmComponentDao.save(component);
                updated++;
            }
        }
        return updated;
    }

    private static boolean isCoreComponent(BpmComponent component) {
        if (component == null) {
            return false;
        }
        String key = component.getKey();
        if (StringUtils.isNotBlank(key) && CoreComponentKeys.contains(key)) {
            return true;
        }
        String id = component.getId();
        if (id != null && id.startsWith(PluginPrefix)) {
            return CoreComponentKeys.contains(id.substring(PluginPrefix.length()));
        }
        return false;
    }

    private static String migratePluginIdsInText(String text) {
        String result = text;
        int searchFrom = 0;
        while (true) {
            int idx = result.indexOf(PluginPrefix, searchFrom);
            if (idx < 0) {
                break;
            }
            int keyStart = idx + PluginPrefix.length();
            int keyEnd = keyStart;
            while (keyEnd < result.length()) {
                char ch = result.charAt(keyEnd);
                if (Character.isLetterOrDigit(ch) || ch == '_') {
                    keyEnd++;
                } else {
                    break;
                }
            }
            String key = result.substring(keyStart, keyEnd);
            if (CoreComponentKeys.contains(key)) {
                result = result.substring(0, idx) + ClasspathPrefix + key + result.substring(keyEnd);
                searchFrom = idx + ClasspathPrefix.length() + key.length();
            } else {
                searchFrom = keyEnd;
            }
        }
        return result;
    }

    private static boolean shouldMigrateId(String id) {
        return id != null && id.startsWith(PluginPrefix) && CoreComponentKeys.contains(id.substring(PluginPrefix.length()));
    }

    private static String toClasspathId(String pluginId) {
        return ClasspathPrefix + pluginId.substring(PluginPrefix.length());
    }
}
