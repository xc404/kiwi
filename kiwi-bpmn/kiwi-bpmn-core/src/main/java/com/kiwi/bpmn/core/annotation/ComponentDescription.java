package com.kiwi.bpmn.core.annotation;


import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

@Target({METHOD, TYPE, ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ComponentDescription
{

    String name();

    String description() default "";

    String version() default "";

    String group() default "Common";

    /**
     * 输入变量映射：每项 {@link ComponentParameter} 的 {@code key} 为组件可读取的参数名。
     * <p>
     * 与 {@code defaultValue} 的约定（属性面板 / Schema 等）：
     * <ul>
     *   <li>当参数 {@code required=true} 且未显式声明默认值时，默认值应为
     *   {@code ${key}}（例如 key=message 时默认值为 {@code ${message}}）。</li>
     *   <li>当显式设置了 {@code defaultValue} 时，以显式值为准。</li>
     * </ul>
     */
    ComponentParameter[] inputs() default {};

    /**
     * 输出变量映射：每项 {@link ComponentParameter} 的 {@code key} 为输出在流程上下文中的变量名（或映射键）。
     * <p>
     * 与 {@code defaultValue} 的约定（属性面板 / Schema 等）：
     * <ul>
     *   <li><strong>非空</strong>：该输出在组件实现中无论用户如何配置都会被写入时，{@code defaultValue} 应为该输出项的
     *   {@code key}；否则表示设计器为该映射提供了显式默认值。</li>
     *   <li><strong>为空</strong>：无固定默认映射名，由用户在配置中指定目标变量；或运行时是否写入取决于配置 /
     *   分支，不提供与 {@code key} 绑定的默认映射。</li>
     * </ul>
     */
    ComponentParameter[] outputs() default {};
}
