package com.kiwi.bpmn.assistant;

import lombok.Data;

/**
 * AI 写工作流生成/改图规则。
 * <p>{@code soft} 写入 LLM prompt；{@code hard} 由校验器强制执行。</p>
 */
@Data
public class AssistantRule {
    private String id;
    /** soft | hard */
    private String kind;
    /** create | modify | both */
    private String mode;
    private String severity;
    private String message;
    private String promptText;
    private boolean enabled = true;

    public boolean appliesTo(String authoringMode) {
        if (!enabled) {
            return false;
        }
        String m = mode == null ? "both" : mode;
        return "both".equalsIgnoreCase(m) || m.equalsIgnoreCase(authoringMode);
    }

    public boolean isSoft() {
        return "soft".equalsIgnoreCase(kind);
    }

    public boolean isHard() {
        return "hard".equalsIgnoreCase(kind);
    }
}
