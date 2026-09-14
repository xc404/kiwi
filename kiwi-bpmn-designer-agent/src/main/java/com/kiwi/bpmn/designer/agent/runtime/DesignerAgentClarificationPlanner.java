package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.agent.model.ClarificationForm;
import com.kiwi.bpmn.designer.agent.model.ClarificationOption;
import com.kiwi.bpmn.designer.agent.model.ClarificationQuestion;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 轻量启发式澄清问卷（避免在简单指令上阻塞；复杂/歧义场景才出题）。
 */
@Component
public class DesignerAgentClarificationPlanner {

    private final ObjectMapper objectMapper;

    public DesignerAgentClarificationPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<String> buildFormJson(String userScenario) {
        if (StringUtils.isBlank(userScenario)) {
            return Optional.empty();
        }
        String text = userScenario.trim();
        if (text.length() < 8) {
            return Optional.empty();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<ClarificationQuestion> questions = new ArrayList<>();

        if (containsAny(lower, "审批", "审核", "approve", "review")) {
            ClarificationQuestion q = new ClarificationQuestion();
            q.setId("1");
            q.setPrompt("审批路径希望怎样组织？");
            q.getOptions().add(option("A", "单人审批即可"));
            q.getOptions().add(option("B", "多级/串行审批"));
            q.getOptions().add(option("C", "并行会签后再汇总"));
            questions.add(q);
        }
        if (containsAny(lower, "分支", "网关", "条件", "如果", "判断")) {
            ClarificationQuestion q = new ClarificationQuestion();
            q.setId("2");
            q.setPrompt("分支决策更偏向哪种网关？");
            q.getOptions().add(option("A", "排他网关（互斥分支）"));
            q.getOptions().add(option("B", "并行网关（同时执行）"));
            q.getOptions().add(option("C", "包容网关（多分支可选）"));
            questions.add(q);
        }
        if (containsAny(lower, "通知", "消息", "邮件", "站内", "kafka", "http")) {
            ClarificationQuestion q = new ClarificationQuestion();
            q.setId("3");
            q.setPrompt("外部集成的优先级？");
            q.getOptions().add(option("A", "仅站内/流程内"));
            q.getOptions().add(option("B", "HTTP 回调为主"));
            q.getOptions().add(option("C", "消息队列/Kafka"));
            questions.add(q);
        }

        if (questions.isEmpty()) {
            return Optional.empty();
        }
        if (questions.size() > 5) {
            questions = questions.subList(0, 5);
        }
        ClarificationForm form = new ClarificationForm();
        form.setQuestions(questions);
        try {
            return Optional.of(objectMapper.writeValueAsString(form));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static ClarificationOption option(String id, String label) {
        ClarificationOption o = new ClarificationOption();
        o.setId(id);
        o.setLabel(label);
        return o;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
