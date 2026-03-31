package com.kiwi.common.query;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface QueryField
{
    String value() default "";

    Type condition() default Type.EQ;

    enum Type
    {
        EQ,//等于
        LIKE,//模糊查询
        IN,// in
        GTE,//大于等于
        LTE,    //小于等于
        BETWEEN,//范围
    }
}
