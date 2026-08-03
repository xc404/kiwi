package com.kiwi.project.ai.authoring;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiAuthoringCatalog {
    private List<CatalogComponent> installed = new ArrayList<>();
    private List<CatalogComponent> installable = new ArrayList<>();
    private List<CatalogTemplate> templates = new ArrayList<>();

    @Data
    public static class CatalogComponent {
        private String id;
        private String name;
        private String source;
        private String group;
        /** installed | available_to_install */
        private String status;
        private String pluginHint;
        private boolean requiresInstall;
    }

    @Data
    public static class CatalogTemplate {
        private String packId;
        private String name;
        private String summary;
        private List<String> tags = new ArrayList<>();
    }
}
