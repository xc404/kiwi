package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.agent.model.ClarificationForm;
import com.kiwi.bpmn.designer.agent.model.ClarificationOption;
import com.kiwi.bpmn.designer.agent.model.ClarificationQuestion;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 合并澄清选项与补充说明，供 conversationHistory 与 LLM 上下文使用。 */
public final class DesignerAgentClarificationMerger {

    private DesignerAgentClarificationMerger() {
    }

    public record MergeResult(String humanSummary, String contextJson, String augmentedScenario) {
    }

    public static MergeResult merge(
            ObjectMapper objectMapper,
            String originalScenario,
            String clarificationFormJson,
            Map<String, Object> answers,
            List<String> skippedQuestionIds,
            String supplementalText) throws Exception {
        String base = StringUtils.defaultString(originalScenario).trim();
        String supplement = StringUtils.trimToNull(supplementalText);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("answers", answers != null ? answers : Map.of());
        context.put("skipped", skippedQuestionIds != null ? skippedQuestionIds : List.of());
        context.put("supplementalText", supplement);

        StringBuilder human = new StringBuilder("[澄清答复]\n");
        if (StringUtils.isNotBlank(clarificationFormJson)) {
            ClarificationForm form = objectMapper.readValue(clarificationFormJson, ClarificationForm.class);
            if (form.getQuestions() != null) {
                for (ClarificationQuestion q : form.getQuestions()) {
                    if (q == null || StringUtils.isBlank(q.getId())) {
                        continue;
                    }
                    if (skippedQuestionIds != null && skippedQuestionIds.contains(q.getId())) {
                        human.append("Q").append(q.getId()).append(" ").append(q.getPrompt()).append(": （跳过）\n");
                        continue;
                    }
                    Object raw = answers != null ? answers.get(q.getId()) : null;
                    if (raw == null) {
                        continue;
                    }
                    String label = formatAnswerLabel(q, raw);
                    human.append("Q").append(q.getId()).append(" ").append(q.getPrompt()).append(": ").append(label).append("\n");
                }
            }
        }
        if (supplement != null) {
            human.append("补充说明: ").append(supplement).append('\n');
        }

        String contextJson = objectMapper.writeValueAsString(context);
        String augmented = base;
        if (!human.isEmpty()) {
            augmented = base + "\n\n" + human.toString().trim();
        } else if (supplement != null) {
            augmented = base + "\n\n" + supplement;
        }

        return new MergeResult(human.toString().trim(), contextJson, augmented);
    }

    private static String formatAnswerLabel(ClarificationQuestion q, Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        String optionId = String.valueOf(raw);
        if (q.getOptions() != null) {
            for (ClarificationOption o : q.getOptions()) {
                if (o != null && optionId.equals(o.getId())) {
                    return optionId + "（" + o.getLabel() + "）";
                }
            }
        }
        return optionId;
    }
}
