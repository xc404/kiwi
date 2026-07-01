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
import java.util.Locale;

/**
 * 官方组件插件化：将非 Slurm 的 {@code classpath_*} 组件 id 迁移为 {@code plugin_*}。
 */
@ChangeUnit(id = "20250629-002-bpm-component-classpath-to-plugin", order = "002", author = "kiwi")
@Slf4j
@RequiredArgsConstructor
public class BpmComponentClasspathToPluginChangeUnit {

    private static final String ClasspathPrefix = "classpath_";
    private static final String PluginPrefix = "plugin_";

    private final BpmProcessDefinitionDao bpmProcessDao;
    private final BpmComponentDao bpmComponentDao;

    @Execution
    public void execute() {
        int processCount = migrateProcesses();
        int componentCount = migrateComponents();
        log.info(
                "BPM 组件 classpath→plugin 迁移完成: processesUpdated={} componentsUpdated={}",
                processCount,
                componentCount);
    }

    @RollbackExecution
    public void rollback() {
        log.warn("BPM 组件 classpath→plugin 迁移不支持自动回滚");
    }

    private int migrateProcesses() {
        List<BpmProcess> all = bpmProcessDao.findAll();
        int updated = 0;
        for (BpmProcess process : all) {
            String xml = process.getBpmnXml();
            if (StringUtils.isBlank(xml) || !xml.contains(ClasspathPrefix)) {
                continue;
            }
            String migrated = migrateClasspathIdsInText(xml);
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
                String newId = toPluginId(component.getId());
                if (!newId.equals(component.getId())) {
                    component.setId(newId);
                    changed = true;
                }
            }
            if (shouldMigrateId(component.getParentId())) {
                String newParent = toPluginId(component.getParentId());
                if (!newParent.equals(component.getParentId())) {
                    component.setParentId(newParent);
                    changed = true;
                }
            }
            if ("classpath".equals(component.getSource())) {
                component.setSource("plugin");
                changed = true;
            }
            if (changed) {
                bpmComponentDao.save(component);
                updated++;
            }
        }
        return updated;
    }

    private static String migrateClasspathIdsInText(String text) {
        String result = text;
        int searchFrom = 0;
        while (true) {
            int idx = result.indexOf(ClasspathPrefix, searchFrom);
            if (idx < 0) {
                break;
            }
            int keyStart = idx + ClasspathPrefix.length();
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
            if (!isSlurmRelatedKey(key)) {
                result = result.substring(0, idx) + PluginPrefix + key + result.substring(keyEnd);
                searchFrom = idx + PluginPrefix.length() + key.length();
            } else {
                searchFrom = keyEnd;
            }
        }
        return result;
    }

    private static boolean shouldMigrateId(String id) {
        return id != null && id.startsWith(ClasspathPrefix) && !isSlurmRelatedKey(id.substring(ClasspathPrefix.length()));
    }

    private static String toPluginId(String classpathId) {
        return PluginPrefix + classpathId.substring(ClasspathPrefix.length());
    }

    private static boolean isSlurmRelatedKey(String key) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("slurm");
    }
}
