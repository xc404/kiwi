package com.kiwi.bpmn.core.async;

import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParser;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.util.xml.Element;

/**
 * 解析 BPMN 时为 Service Task 默认设置 {@code asyncBefore}，在易失败节点前形成事务边界，
 * 使实例与历史可先提交；失败由 Job/Incident 呈现。可通过 XML 显式 {@code camunda:asyncBefore="false"} 关闭单节点。
 * <p>
 * External Task（{@code camunda:type="external"}）跳过，避免与既有异步语义叠加。
 * <p>
 * 由 {@link DefaultAsyncBeforeProcessEnginePlugin} 在开启 {@code kiwi.bpm.default-async-before-enabled} 时注册，非独立 Bean。
 */
public class DefaultAsyncBeforeBpmnParseListener extends AbstractBpmnParseListener {

    @Override
    public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope, ActivityImpl activity) {
        String camundaType =
                serviceTaskElement.attribute(BpmnParser.CAMUNDA_BPMN_EXTENSIONS_NS, "type");
        if ("external".equalsIgnoreCase(camundaType)) {
            return;
        }
        String explicitAsyncBefore =
                serviceTaskElement.attribute(BpmnParser.CAMUNDA_BPMN_EXTENSIONS_NS, "asyncBefore");
        if ("false".equalsIgnoreCase(explicitAsyncBefore)) {
            return;
        }
        if (!activity.isAsyncBefore()) {
            activity.setAsyncBefore(true);
        }
    }
}
