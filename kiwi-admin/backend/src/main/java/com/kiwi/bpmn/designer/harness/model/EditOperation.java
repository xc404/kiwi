package com.kiwi.bpmn.designer.harness.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条改图操作。{@code op}：addNode | removeNode | updateNode | addFlow | removeFlow | setProcessMeta。
 */
@Data
public class EditOperation {
    private String op;
    private NodeSpec node;
    private String nodeId;
    private NodeSpec patch;
    private FlowSpec flow;
    private String flowId;
    private String afterRef;
    private String beforeRef;
    private String name;
}