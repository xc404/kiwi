package com.kiwi.cryoems.bpm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 暴露给 Camunda BPMN EL 表达式直接引用的「BPM 全局变量」。
 * <p>
 * 用法：在 BPMN 中通过 Bean 名 {@code bpmGlobalVariables} 取字段值，例如：
 * <pre>{@code
 * <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">
 *   ${bpmGlobalVariables.mdocAutoFilterEnabled}
 * </bpmn:conditionExpression>
 * }</pre>
 * <p>
 * 背景：{@link org.springframework.beans.factory.annotation.Value @Value("${...}")}
 * 是 Spring 占位符，仅在 Java 类内部生效；BPMN 的 {@code ${...}} 是 JUEL，
 * 在项目使用的 {@code SpringExpressionManager} 下解析为「流程变量 → Spring Bean → 属性/方法」三类对象。
 * 因此把 {@code application.yml} 配置封装成具名 Bean，是 BPMN 直接读取静态/全局配置最稳妥的方式。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>{@link Component @Component("bpmGlobalVariables")} 显式 Bean 名，避免类名重命名导致 BPMN 表达式失效。</li>
 *   <li>{@link ConfigurationProperties @ConfigurationProperties} 与 yml 字段（kebab-case）自动绑定为 camelCase。</li>
 *   <li>所有字段提供安全默认值，未在 yml 配置时也可正常启动。</li>
 *   <li>该 Bean 承载「按部署固定」的全局值；若需「按流程实例固化」请改用 {@code execution.setVariable}。</li>
 *   <li>{@code @ConfigurationProperties} 默认不热更新，配置变更需重启应用 + 新启动的流程实例方可生效。</li>
 * </ul>
 *
 * @see <a href="file:application.yml">kiwi.bpm.global.*</a>
 */
@Component("bpmGlobalVariables")
@ConfigurationProperties(prefix = "kiwi.bpm.global")
@Data
public class BpmGlobalVariables {

    /**
     * mdoc 重建分支：是否启用「自动堆叠 + 过滤」分支（{@code CryoemsMdocStackAndFilterActivity}）。
     * <p>
     * 关闭（默认）时走「手动堆叠」分支（{@code CryoemsMdocStackActivity}），由用户在管理端确认 / 触发后续 SLURM。
     * <p>
     * BPMN 用法（Exclusive Gateway 出向 sequenceFlow）：
     * <pre>{@code ${bpmGlobalVariables.mdocAutoFilterEnabled}}</pre>
     * 配置键：{@code kiwi.bpm.global.mdoc-auto-filter-enabled}。
     */
    private boolean mdocAutoFilterEnabled = false;

    /**
     * mdoc 流程：是否允许进入「手动重建」分支。
     * <p>
     * 关闭后，即使 {@code mdoc.manualRebuild=true} 也将走自动分支（需配合 BPMN 网关条件设计）。
     * 配置键：{@code kiwi.bpm.global.manual-rebuild-enabled}。
     */
    private boolean manualRebuildEnabled = true;
}
