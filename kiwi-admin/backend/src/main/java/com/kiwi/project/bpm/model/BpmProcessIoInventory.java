package com.kiwi.project.bpm.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 按组件拓扑序展开的流程 IO 清单（设计时配置，不含运行时实例变量）。
 */
@Data
public class BpmProcessIoInventory {

    private List<Node> nodes = new ArrayList<>();
    private List<StartVariable> startVariables = new ArrayList<>();
    /**
     * 全流程目录输出并集，按拓扑序合并，同名 key 以后出现的组件为准。
     * 供另存为组件等流程级契约使用。
     */
    private List<BpmComponentParameter> processOutputs = new ArrayList<>();

    @Data
    public static class Node {
        private String nodeId;
        private String name;
        private String componentId;
        private String componentName;
        private List<Param> inputs = new ArrayList<>();
        private List<Param> outputs = new ArrayList<>();
    }

    @Data
    public static class Param {
        private String key;
        private String name;
        private String description;
        private boolean required;
        private Direction direction;
        private boolean filled;
        private ValueKind valueKind;
        private String configuredValue;
        private List<String> expressionRefs = new ArrayList<>();
        private boolean satisfiedByUpstream;
        /** 输出写入的流程变量名（目录 defaultValue 或 key）。 */
        private String processVariable;
    }

    @Data
    public static class StartVariable {
        private String key;
        private String name;
        private String description;
        private boolean required;
        private String nodeId;
        private String parameterKey;
    }

    public enum Direction {
        Input,
        Output
    }

    public enum ValueKind {
        Empty,
        Literal,
        Expression
    }
}
