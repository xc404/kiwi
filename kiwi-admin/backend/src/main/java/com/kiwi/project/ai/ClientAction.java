package com.kiwi.project.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端可执行的动作（多态）。JSON 与历史扁平结构兼容：顶层 {@code type} 为 discriminator，
 * 其余字段由各子类型独占，不再混在一个「万能」对象里。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ClientAction.NavigateClientAction.class, name = "navigate"),
    @JsonSubTypes.Type(value = ClientAction.ToolbarClientAction.class, name = "toolbar"),
    @JsonSubTypes.Type(value = ClientAction.BpmnXmlClientAction.class, name = "bpmnXml"),
    @JsonSubTypes.Type(value = ClientAction.AppendComponentClientAction.class, name = "appendComponent")
})
public sealed interface ClientAction permits ClientAction.NavigateClientAction,
        ClientAction.ToolbarClientAction,
        ClientAction.BpmnXmlClientAction,
        ClientAction.AppendComponentClientAction {

    /** 不参与序列化；JSON 的 {@code type} 由 Jackson 子类型机制写入。 */
    @JsonIgnore
    ClientActionType actionType();

    static NavigateClientAction navigate(String path, Map<String, String> queryParams) {
        Map<String, String> q = queryParams != null ? new LinkedHashMap<>(queryParams) : new LinkedHashMap<>();
        return new NavigateClientAction(path != null ? path.trim() : "", q);
    }

    static AppendComponentClientAction appendComponent(String componentId, String sourceElementId) {
        String cid = componentId != null ? componentId.trim() : "";
        String sid = sourceElementId != null && !sourceElementId.isBlank() ? sourceElementId.trim() : null;
        return new AppendComponentClientAction(cid, sid);
    }

    record NavigateClientAction(String path, Map<String, String> queryParams) implements ClientAction {

        public NavigateClientAction {
            if (path == null) {
                throw new IllegalArgumentException("path 不能为空。");
            }
            queryParams = queryParams != null ? new LinkedHashMap<>(queryParams) : new LinkedHashMap<>();
        }

        @Override
        @JsonIgnore
        public ClientActionType actionType() {
            return ClientActionType.NAVIGATE;
        }
    }

    record ToolbarClientAction(String toolbarCommand, Map<String, Object> toolbarOptions) implements ClientAction {

        public ToolbarClientAction {
            if (toolbarCommand == null || toolbarCommand.isBlank()) {
                throw new IllegalArgumentException("toolbarCommand 不能为空。");
            }
            toolbarOptions = toolbarOptions != null ? new LinkedHashMap<>(toolbarOptions) : new LinkedHashMap<>();
        }

        @Override
        @JsonIgnore
        public ClientActionType actionType() {
            return ClientActionType.TOOLBAR;
        }
    }

    record BpmnXmlClientAction(String xml) implements ClientAction {

        public BpmnXmlClientAction {
            if (xml == null || xml.isBlank()) {
                throw new IllegalArgumentException("xml 不能为空。");
            }
        }

        @Override
        @JsonIgnore
        public ClientActionType actionType() {
            return ClientActionType.BPMN_XML;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AppendComponentClientAction(String componentId, String sourceElementId) implements ClientAction {

        public AppendComponentClientAction {
            if (componentId == null || componentId.isBlank()) {
                throw new IllegalArgumentException("componentId 不能为空。");
            }
        }

        @Override
        @JsonIgnore
        public ClientActionType actionType() {
            return ClientActionType.APPEND_COMPONENT;
        }
    }
}
