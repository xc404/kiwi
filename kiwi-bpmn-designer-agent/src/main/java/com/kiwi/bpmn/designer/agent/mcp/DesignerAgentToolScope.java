package com.kiwi.bpmn.designer.agent.mcp;

import java.util.Set;

/**
 * 设计器 Agent 可调用的 MCP 工具白名单（OpenAPI operationId）。
 */
public final class DesignerAgentToolScope {

    public static final Set<String> DiscoveryToolNames = Set.of(
            "bpmComp_aiPage",
            "bpmComp_listGrouped",
            "bpmComp_recentUsage",
            "bpmRemoteMarket_list",
            "bpmRemoteMarket_get",
            "bpmMarket_aiPage",
            "bpmMarket_get",
            "bpmMarket_getProcess",
            "bpmPd_aiPage",
            "bpmPd_get",
            "bpmProjEnv_list");

    public static final Set<String> WriteToolNames = Set.of(
            "bpmPd_save",
            "bpmPd_deploy",
            "bpmPd_start",
            "bpmRemoteMarket_installPlugin");

    private DesignerAgentToolScope() {
    }
}
