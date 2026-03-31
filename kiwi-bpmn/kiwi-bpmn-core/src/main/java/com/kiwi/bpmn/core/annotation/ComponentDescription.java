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

    ComponentParameter[] inputs() default {};

    ComponentParameter[] outputs() default  {};
}
