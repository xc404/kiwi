package com.cryo.common.model;

import lombok.Getter;

import java.util.Collection;

public class ContentResult<T> {
    @Getter
    private Collection<T> content;

    public ContentResult(Collection<T> content) {
        this.content = content;
    }
}
