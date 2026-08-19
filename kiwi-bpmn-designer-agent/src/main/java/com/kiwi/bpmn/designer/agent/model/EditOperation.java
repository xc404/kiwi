package com.kiwi.bpmn.designer.agent.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单条编辑操作。{@code op} 取值：addNode | removeNode | updateNode | addFlow | removeFlow | setProcessMeta。
 */
@Data
public class EditOperation {
    private String op;
    /** addNode / updateNode */
    private NodeSpec node;
    /** removeNode / updateNode */
    private String nodeId;
    /** updateNode 局部字段 */
    private NodeSpec patch;
    /** addFlow */
    private FlowSpec flow;
    /** removeFlow */
    private String flowId;
    /** addNode：插入在连线 source→target 之间，或接在 afterRef 之后 */
    private String afterRef;
    private String beforeRef;
    /** setProcessMeta */
    private String name;
}
