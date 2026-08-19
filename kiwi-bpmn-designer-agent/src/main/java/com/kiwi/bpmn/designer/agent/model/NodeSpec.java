package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class NodeSpec {
    private String id;
    /** startEvent | endEvent | serviceTask | userTask | exclusiveGateway */
    private String type;
    private String name;
    private String componentId;
    private Map<String, Object> parameters = new LinkedHashMap<>();
}
