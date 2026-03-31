package com.kiwi.framework.web.response;

import lombok.Getter;

import java.util.Collection;
/**
 * 集合结果
 * 为了兼容前端，返回集合结果时，将集合结果封装成CollectionResult，方便以后扩展分页模式
 * @param <T>
 */
public class CollectionResult<T> {
    @Getter
    private Collection<T> content;

    public CollectionResult(Collection<T> content) {
        this.content = content;
    }
}
