package com.kiwi.bpmn.assistant.spi;

import java.util.List;
import java.util.Optional;

/**
 * 解析已装 / 可装组件元数据，供编译器与校验器使用（宿主实现，如 kiwi-admin）。
 */
public interface AssistantComponentLookup {

    /** 组件是否可被引擎解析（已装 classpath/plugin，含 plugin_/classpath_ 别名）。 */
    boolean exists(String componentId);

    /** 已解析组件的必填 input 参数 key；不存在时返回空列表。 */
    List<String> requiredInputKeys(String componentId);

    /**
     * 解析已装组件的 camunda delegateExpression；含别名。
     * 未安装时 empty。
     */
    Optional<String> resolveDelegateExpression(String componentId);

    /**
     * 若组件因插件未安装而缺失，返回安装提示（如 jar 名 / 市场 hint JSON / componentId）；
     * 否则 empty。
     */
    Optional<String> pluginMissingHint(String componentId);
}
