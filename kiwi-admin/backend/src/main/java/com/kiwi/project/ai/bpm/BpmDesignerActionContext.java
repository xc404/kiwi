package com.kiwi.project.ai.bpm;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 BPM 设计器助手请求内收集 {@link BpmDesignerAction}（与工具回调同线程）。
 */
@Component
public class BpmDesignerActionContext {

    private static final ThreadLocal<List<BpmDesignerAction>> ACTIONS =
            ThreadLocal.withInitial(ArrayList::new);

    public void beginRequest() {
        ACTIONS.remove();
    }

    public void add(BpmDesignerAction action) {
        ACTIONS.get().add(action);
    }

    public List<BpmDesignerAction> drainActions() {
        try {
            return List.copyOf(ACTIONS.get());
        } finally {
            ACTIONS.remove();
        }
    }
}
