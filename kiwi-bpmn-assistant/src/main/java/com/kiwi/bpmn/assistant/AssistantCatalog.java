package com.kiwi.bpmn.assistant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssistantCatalog {
    private List<CatalogComponent> installed = new ArrayList<>();
    private List<CatalogComponent> installable = new ArrayList<>();
    private List<CatalogTemplate> templates = new ArrayList<>();

    @Data
    public static class CatalogComponent {
        private String id;
        private String name;
        private String description;
        private String source;
        private String group;
        private String delegateExpression;
        private List<CatalogParameter> inputs = new ArrayList<>();
        /** installed | available_to_install */
        private String status;
        private String pluginHint;
        private String marketSlug;
        private String marketVersion;
        private String marketSourceId;
        private boolean requiresInstall;
    }

    @Data
    public static class CatalogParameter {
        private String key;
        private String description;
        private String type;
        private String defaultValue;
        private String example;
        private boolean required;
    }

    @Data
    public static class CatalogTemplate {
        private String packId;
        private String name;
        private String summary;
        private List<String> tags = new ArrayList<>();
        /** 仅 Top-1 模板携带，作为 LLM 结构参考并受长度上限约束。 */
        private String referenceProcessKey;
        private String referenceBpmnXml;
    }
}
