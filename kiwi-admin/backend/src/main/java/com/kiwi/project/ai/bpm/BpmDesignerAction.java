package com.kiwi.project.ai.bpm;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端可执行的 BPM 设计器动作（由 {@link BpmDesignerTools} 登记，经接口返回）。
 */
@Data
public class BpmDesignerAction {

    /**
     * toolbar | bpmnXml | appendComponent | navigate
     */
    private String type;

    /** toolbar：与前端 {@code BpmDesignerAiBridge} 约定的一致命令字。 */
    private String toolbarCommand;

    /** toolbar：传给 bpmn editorActions 的附加参数（如 stepZoom 的 value）。 */
    private Map<String, Object> toolbarOptions;

    private String xml;

    /** appendComponent：组件库中的组件 id（与 {@link com.kiwi.project.bpm.model.BpmComponent#getId()} 一致）。 */
    private String componentId;

    private String sourceElementId;

    private String path;
    private Map<String, String> queryParams;

    public static BpmDesignerAction toolbar(String command, Map<String, Object> options) {
        BpmDesignerAction a = new BpmDesignerAction();
        a.setType("toolbar");
        a.setToolbarCommand(command);
        a.setToolbarOptions(options != null ? options : new LinkedHashMap<>());
        return a;
    }

    public static BpmDesignerAction bpmnXml(String xml) {
        BpmDesignerAction a = new BpmDesignerAction();
        a.setType("bpmnXml");
        a.setXml(xml);
        return a;
    }

    public static BpmDesignerAction appendComponent(String componentId, String sourceElementId) {
        BpmDesignerAction a = new BpmDesignerAction();
        a.setType("appendComponent");
        a.setComponentId(componentId);
        a.setSourceElementId(sourceElementId);
        return a;
    }

    public static BpmDesignerAction navigate(String path, Map<String, String> queryParams) {
        BpmDesignerAction a = new BpmDesignerAction();
        a.setType("navigate");
        a.setPath(path);
        a.setQueryParams(queryParams != null ? queryParams : new LinkedHashMap<>());
        return a;
    }
}
