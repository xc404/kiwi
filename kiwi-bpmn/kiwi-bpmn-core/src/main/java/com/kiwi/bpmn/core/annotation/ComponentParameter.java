package com.kiwi.bpmn.core.annotation;

import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;

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
public @interface ComponentParameter
{
    String key();
    String name() default "";

    ParameterIn in() default ParameterIn.DEFAULT;

    String description() default "";

    boolean required() default false;

    boolean deprecated() default false;

    boolean allowEmptyValue() default false;

    ParameterStyle style() default ParameterStyle.DEFAULT;

    Explode explode() default Explode.DEFAULT;

    boolean allowReserved() default false;

    Schema schema() default @Schema;

    ArraySchema array() default @ArraySchema;

    Content[] content() default {};

    boolean hidden() default false;

    ExampleObject[] examples() default {};

    String example() default "";

    Extension[] extensions() default {};

    String ref() default "";

    Class<?>[] validationGroups() default {};

    String htmlType() default "";

    String type() default "string";
}
