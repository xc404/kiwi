package com.cryo.common.query;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface QueryField {
    String value() default "";

    QueryParam.QueryFieldOP op() default QueryParam.QueryFieldOP.EQ;
}
