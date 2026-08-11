package com.kiwi.bpmn.assistant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 创建/修改 BPMN 时的规则集：软规则进 prompt，硬规则由校验器引用。
 */
@Component
public class AssistantRuleSet {

    public static final String ModeCreate = "create";
    public static final String ModeModify = "modify";

    public static final String RuleComponentIdInCatalog = "component_id_in_catalog";
    public static final String RuleRequiredParamsPresent = "required_params_present";
    public static final String RuleHasStartAndEnd = "has_start_and_end";
    public static final String RuleSequenceFlowEndpoints = "sequence_flow_endpoints_valid";
    public static final String RuleModifyPreserveUnrelated = "modify_preserve_unrelated";
    public static final String RuleOutputJsonOnly = "output_json_only";
    public static final String RuleSummaryForUsers = "summary_for_users";

    private final ObjectMapper objectMapper;
    private List<AssistantRule> rules = List.of();

    public AssistantRuleSet(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        rules = loadOrBuiltin();
    }

    /** 测试或手动注入用 */
    public void replaceRules(List<AssistantRule> next) {
        this.rules = next != null ? List.copyOf(next) : List.of();
    }

    public List<AssistantRule> all() {
        return rules;
    }

    public List<AssistantRule> forMode(String mode, String kind) {
        String m = normalizeMode(mode);
        List<AssistantRule> out = new ArrayList<>();
        for (AssistantRule r : rules) {
            if (!r.appliesTo(m)) {
                continue;
            }
            if (kind != null && !kind.equalsIgnoreCase(r.getKind())) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    public Optional<AssistantRule> find(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        return rules.stream().filter(r -> id.equals(r.getId())).findFirst();
    }

    public Optional<AssistantRule> findHard(String id) {
        if (StringUtils.isBlank(id)) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(AssistantRule::isHard)
                .filter(r -> id.equals(r.getId()))
                .findFirst();
    }

    public String renderSoftPrompt(String mode) {
        List<AssistantRule> soft = forMode(mode, "soft");
        if (soft.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("生成规则（请遵守）：\n");
        int i = 1;
        for (AssistantRule r : soft) {
            String text = StringUtils.defaultIfBlank(r.getPromptText(), r.getMessage());
            sb.append(i++).append(") [").append(r.getId()).append("] ").append(text).append('\n');
        }
        return sb.toString().trim();
    }

    public String resolveMode(String previousXml) {
        return StringUtils.isBlank(previousXml) ? ModeCreate : ModeModify;
    }

    private String normalizeMode(String mode) {
        if (ModeModify.equalsIgnoreCase(mode)) {
            return ModeModify;
        }
        return ModeCreate;
    }

    private List<AssistantRule> loadOrBuiltin() {
        try {
            ClassPathResource res = new ClassPathResource("bpm/ai/authoring-rules.json");
            if (res.exists()) {
                try (InputStream in = res.getInputStream()) {
                    List<AssistantRule> loaded = objectMapper.readValue(in, new TypeReference<>() {
                    });
                    if (loaded != null && !loaded.isEmpty()) {
                        return List.copyOf(loaded);
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through to builtin
        }
        return builtin();
    }

    private List<AssistantRule> builtin() {
        List<AssistantRule> list = new ArrayList<>();
        list.add(soft(RuleOutputJsonOnly, "both",
                "只输出 JSON，不要 Markdown："
                        + "{\"summary\":\"给用户看的中文说明（2-6句）\","
                        + "\"planIrJson\":{\"processId\":\"...\",\"nodes\":[],\"flows\":[]}}。"
                        + "禁止输出 candidateXml；服务端仅编译 planIrJson。"));
        list.add(soft(RuleComponentIdInCatalog, "both",
                "componentId 只能使用 Catalog.installed 中的 id；若必须用 installable，在 plan 中标记 requiresInstall=true。"));
        list.add(soft(RuleHasStartAndEnd, "both",
                "Plan IR 必须含 startEvent、endEvent 与完整 flows；至少保留或包含合理业务节点。"));
        list.add(soft(RuleSummaryForUsers, "both",
                "summary 面向业务用户，不要提内部变量名或实例 id。"));
        list.add(soft(RuleModifyPreserveUnrelated, "modify",
                "在 basePlanIr 上按用户要求修改，保留无关节点/连线/参数，不要无故整图重写；勿擅自更改 process id。输出完整修改后的 planIrJson。"));
        list.add(hard(RuleHasStartAndEnd, "both", "REPAIR",
                "流程必须包含 startEvent 与 endEvent"));
        list.add(hard(RuleSequenceFlowEndpoints, "both", "REPAIR",
                "sequenceFlow 的 sourceRef/targetRef 必须指向图中存在的节点"));
        list.add(hard(RuleComponentIdInCatalog, "both", "REPAIR",
                "componentId 必须出现在本轮 Catalog（installed 或 installable）"));
        list.add(hard(RuleRequiredParamsPresent, "both", "REPAIR",
                "已解析组件的必填参数必须出现在该节点的 inputParameter 中"));
        return list;
    }

    private AssistantRule soft(String id, String mode, String promptText) {
        AssistantRule rule = new AssistantRule();
        rule.setId(id);
        rule.setKind("soft");
        rule.setMode(mode);
        rule.setPromptText(promptText);
        rule.setMessage(promptText);
        return rule;
    }

    private AssistantRule hard(String id, String mode, String severity, String message) {
        AssistantRule rule = new AssistantRule();
        rule.setId(id);
        rule.setKind("hard");
        rule.setMode(mode);
        rule.setSeverity(severity);
        rule.setMessage(message);
        rule.setPromptText(message);
        return rule;
    }
}
