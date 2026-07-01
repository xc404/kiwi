package com.kiwi.project.bpm.dto;

import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplatePackManifest;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BpmTemplatePackDetailDto {

    private BpmTemplatePack pack;
    private List<BpmTemplateProcessSummary> processes = new ArrayList<>();
    private List<String> envKeys = new ArrayList<>();
    private BpmTemplatePackManifest manifest;

    @Data
    public static class BpmTemplateProcessSummary {
        private String id;
        private String processKey;
        private String name;
        private boolean entry;
        private Integer sort;
    }

    public static BpmTemplateProcessSummary fromProcess(BpmTemplateProcess p) {
        BpmTemplateProcessSummary s = new BpmTemplateProcessSummary();
        s.setId(p.getId());
        s.setProcessKey(p.getProcessKey());
        s.setName(p.getName());
        s.setEntry(p.isEntry());
        s.setSort(p.getSort());
        return s;
    }
}
