package com.kiwi.bpmn.designer.harness;

import com.kiwi.bpmn.designer.harness.persist.DesignerHarnessSession;
import org.springframework.stereotype.Component;

/**
 * 一轮 turn 内工具看到的当前会话（ThreadLocal）。
 */
@Component
public class DesignerHarnessTurnContext {

    private final ThreadLocal<Slot> current = new ThreadLocal<>();

    public void bind(DesignerHarnessSession session, String bpmnXml) {
        Slot slot = new Slot();
        slot.session = session;
        slot.bpmnXml = bpmnXml;
        current.set(slot);
    }

    public void clear() {
        current.remove();
    }

    public Slot require() {
        Slot slot = current.get();
        if (slot == null || slot.session == null) {
            throw new IllegalStateException("工具只能在一轮对话内调用");
        }
        return slot;
    }

    public static class Slot {
        public DesignerHarnessSession session;
        public String bpmnXml;
    }
}