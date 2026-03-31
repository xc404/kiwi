package com.kiwi.common.tree;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TreeNode<T extends Node> implements Node
{


    @JsonUnwrapped
    private final T node;
    private List<TreeNode<T>> children;

    public TreeNode(T node, List<TreeNode<T>> children) {
        this.node = node;
        this.children = children;
    }

    @Override
    public Serializable getId() {
        return node.getId();
    }

    @Override
    public Serializable getParentId() {
        return node.getParentId();
    }

    @Override
    public String getName() {
        return node.getName();
    }

    @Override
    public Integer getSort() {
        return node.getSort();
    }
}
