package com.kiwi.project.ai.assistant.spi;

import com.kiwi.bpmn.assistant.spi.AssistantBpmnLookup;
import com.kiwi.framework.session.SessionService;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import com.kiwi.project.bpm.service.BpmTemplatePackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAssistantBpmnLookup implements AssistantBpmnLookup {

    private final BpmTemplatePackService bpmTemplatePackService;
    private final SessionService sessionService;

    @Override
    public List<TemplateSummary> findMatureTemplates(String scenario, List<String> keywords, int topN) {
        int limit = Math.max(1, topN);
        List<String> kws = keywords != null ? keywords : List.of();
        String userId = safeUserId();
        List<TemplateSummary> out = new ArrayList<>();
        try {
            BpmTemplatePackService.PackQueryInput q = new BpmTemplatePackService.PackQueryInput();
            if (!kws.isEmpty()) {
                q.setKeyword(kws.get(0));
            } else if (StringUtils.isNotBlank(scenario)) {
                q.setKeyword(scenario.length() > 40 ? scenario.substring(0, 40) : scenario);
            }
            var page = bpmTemplatePackService.page(q, PageRequest.of(0, limit), userId);
            for (BpmTemplatePack pack : page.getContent()) {
                TemplateSummary t = new TemplateSummary();
                t.setPackId(pack.getId());
                t.setName(pack.getName());
                t.setSummary(pack.getSummary());
                if (pack.getTags() != null) {
                    t.setTags(new ArrayList<>(pack.getTags()));
                }
                if (out.isEmpty()) {
                    attachReferenceBpmn(t, pack.getId(), userId);
                }
                out.add(t);
            }
        } catch (Exception e) {
            log.warn("AssistantBpmnLookup 模板检索失败: {}", e.toString());
        }
        return out;
    }

    private void attachReferenceBpmn(TemplateSummary target, String packId, String userId) {
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
            String xml = reference.getBpmnXml();
            if (xml.length() > 16_000) {
                xml = xml.substring(0, 16_000) + "…";
            }
            target.setReferenceBpmnXml(xml);
        } catch (RuntimeException ignored) {
            // Catalog 摘要仍可用
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
}
