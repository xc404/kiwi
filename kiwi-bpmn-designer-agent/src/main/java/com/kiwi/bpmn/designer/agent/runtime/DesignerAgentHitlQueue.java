package com.kiwi.bpmn.designer.agent.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.bpmn.designer.agent.model.AgentStreamEvent;
import com.kiwi.bpmn.designer.agent.model.DesignerAgentHitlItem;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 决策队列（Cursor 式 HITL 基础层）：追加条目并 SSE 通知前端时间线。
 */
public final class DesignerAgentHitlQueue {

    private DesignerAgentHitlQueue() {
    }

    public static void enqueue(DesignerAgentRun run, ObjectMapper objectMapper, DesignerAgentHitlItem item) {
        if (run == null || item == null || objectMapper == null) {
            return;
        }
        if (StringUtils.isBlank(item.getId())) {
            item.setId(UUID.randomUUID().toString());
        }
        try {
            List<DesignerAgentHitlItem> items = read(run.getPendingHitlItemsJson(), objectMapper);
            items.add(item);
            run.setPendingHitlItemsJson(objectMapper.writeValueAsString(items));
            AgentStreamEvent pending = AgentStreamEvent.of("hitl_pending");
            pending.setHitlItemJson(objectMapper.writeValueAsString(item));
            pending.setStage(run.getStage());
            run.emit(pending);
        } catch (Exception ignored) {
            // 队列失败不阻断主流程
        }
    }

    private static List<DesignerAgentHitlItem> read(String json, ObjectMapper objectMapper) throws Exception {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        List<DesignerAgentHitlItem> list =
                objectMapper.readValue(json, new TypeReference<List<DesignerAgentHitlItem>>() {});
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }
}
