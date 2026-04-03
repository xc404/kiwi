package com.kiwi.bpmn.component.activity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 单次赋值：写入目标流程变量 {@link #key}，值为 {@link #value}；
 * 若 value 为字符串且整体为 {@code ${varName}}，运行时从流程变量 {@code varName} 取值。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Assignment implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标流程变量名 */
    private String key;
    /** 字面量；字符串且形如 {@code ${x}} 时表示引用变量 x */
    private Object value;
}
