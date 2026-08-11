package com.kiwi.bpmn.assistant.spi;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 按场景/关键词检索成熟流程模板（宿主实现）。
 */
public interface AssistantBpmnLookup {

    List<TemplateSummary> findMatureTemplates(String scenario, List<String> keywords, int topN);

    @Data
    class TemplateSummary {
        private String packId;
        private String name;
        private String summary;
        private List<String> tags = new ArrayList<>();
        private String referenceProcessKey;
        /** Top-1 等场景可携带参考 BPMN；可为空。 */
        private String referenceBpmnXml;
    }
}
