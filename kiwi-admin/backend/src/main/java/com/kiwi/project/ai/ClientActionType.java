package com.kiwi.project.ai;

/**
 * 助手下发给前端的客户端动作语义类型（与 {@link ClientAction} 多态子类一一对应）。
 */
public enum ClientActionType {
    /** 应用内菜单路由跳转 */
    NAVIGATE,
    /** BPM 设计器工具栏命令 */
    TOOLBAR,
    /** 替换/导入 BPMN XML */
    BPMN_XML,
    /** 从组件库追加到画布 */
    APPEND_COMPONENT
}
