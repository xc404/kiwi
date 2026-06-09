package com.kiwi.bpmn.core.el;

import jakarta.el.CompositeELResolver;
import jakarta.el.ELResolver;
import org.operaton.bpm.engine.impl.el.JuelExpressionManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 在 Operaton 已初始化的 {@link JuelExpressionManager}（含 {@code SpringExpressionManager}）
 * 的 EL 解析链末尾追加 {@link MissingIdentifierNullElResolver}，不替换表达式管理器本身。
 */
final class JuelElResolverAugmentation {

    private JuelElResolverAugmentation() {}

    static void appendMissingIdentifierNullResolver(JuelExpressionManager expressionManager) {
        try {
            Method ensureInitialized = JuelExpressionManager.class.getDeclaredMethod("ensureInitialized");
            ensureInitialized.setAccessible(true);
            ensureInitialized.invoke(expressionManager);

            Field elResolverField = JuelExpressionManager.class.getDeclaredField("elResolver");
            elResolverField.setAccessible(true);
            ELResolver resolver = (ELResolver) elResolverField.get(expressionManager);
            if (!(resolver instanceof CompositeELResolver composite)) {
                throw new IllegalStateException(
                        "Unexpected ELResolver type: " + resolver.getClass().getName());
            }
            if (hasMissingIdentifierNullResolver(composite)) {
                return;
            }
            composite.add(new MissingIdentifierNullElResolver());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Failed to augment Operaton JuelExpressionManager EL resolver chain", e);
        }
    }

    private static boolean hasMissingIdentifierNullResolver(CompositeELResolver composite) {
        try {
            Field resolversField = CompositeELResolver.class.getDeclaredField("resolvers");
            resolversField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Iterable<ELResolver> resolvers = (Iterable<ELResolver>) resolversField.get(composite);
            for (ELResolver resolver : resolvers) {
                if (resolver instanceof MissingIdentifierNullElResolver) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // 无法枚举时允许重复追加（无害）
        }
        return false;
    }
}
