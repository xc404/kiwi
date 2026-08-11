package com.kiwi.bpmn.assistant;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 与确定性 BPMN 编译器之间的窄 IR。
 */
@Data
public class AssistantPlan {
    private String processId;
    private String name;
    private List<Node> nodes = new ArrayList<>();
    private List<Flow> flows = new ArrayList<>();

    @Data
    public static class Node {
        private String id;
        /** startEvent | endEvent | serviceTask | userTask | exclusiveGateway */
        private String type;
        private String name;
        /** serviceTask 必填，且必须来自本轮 Catalog。 */
        private String componentId;
        private Map<String, Object> parameters = new LinkedHashMap<>();
    }

    @Data
    public static class Flow {
        private String id;
        private String sourceRef;
        private String targetRef;
        private String condition;
    }
}
