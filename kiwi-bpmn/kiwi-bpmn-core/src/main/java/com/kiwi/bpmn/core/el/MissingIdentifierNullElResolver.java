package com.kiwi.bpmn.core.el;

import org.camunda.bpm.engine.impl.el.VariableScopeElResolver;
import org.camunda.bpm.impl.juel.jakarta.el.ELContext;
import org.camunda.bpm.impl.juel.jakarta.el.ELResolver;

import java.beans.FeatureDescriptor;
import java.util.Iterator;
import java.util.Set;

/**
 * 置于 JUEL {@link org.camunda.bpm.impl.juel.jakarta.el.CompositeELResolver} 链末尾
 * （须在 {@code SpringExpressionManager} / Bean 解析器之后追加）：
 * 当流程变量、Spring Bean 等均未解析顶层标识符时，返回 {@code null} 而非
 * {@code PropertyNotFoundException}。
 * <p>
 * 不处理 {@link VariableScopeElResolver} 保留字（execution / task 等），以便配置错误时仍能抛错。
 */
public class MissingIdentifierNullElResolver extends ELResolver {

    private static final Set<String> RESERVED_ROOTS = Set.of(
            VariableScopeElResolver.EXECUTION_KEY,
            VariableScopeElResolver.CASE_EXECUTION_KEY,
            VariableScopeElResolver.TASK_KEY,
            VariableScopeElResolver.EXTERNAL_TASK_KEY,
            VariableScopeElResolver.LOGGED_IN_USER_KEY);

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base != null || !(property instanceof String name)) {
            return null;
        }
        if (RESERVED_ROOTS.contains(name)) {
            return null;
        }
        context.setPropertyResolved(true);
        return null;
    }

    @Override
    public Class<?> getType(ELContext context, Object base, Object property) {
        return Object.class;
    }

    @Override
    public void setValue(ELContext context, Object base, Object property, Object value) {
        // read-only fallback
    }

    @Override
    public boolean isReadOnly(ELContext context, Object base, Object property) {
        return true;
    }

    @Override
    public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
        return null;
    }

    @Override
    public Class<?> getCommonPropertyType(ELContext context, Object base) {
        return Object.class;
    }
}
