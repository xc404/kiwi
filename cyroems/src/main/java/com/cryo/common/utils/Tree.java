package com.cryo.common.utils;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Tree<T extends Tree.Node> {

    private final Map<Serializable, List<TreeNode<T>>> parentIdMap;
    private Map<Serializable, TreeNode<T>> nodeMap;

    private Tree(List<TreeNode<T>> treeNodes) {
        this.nodeMap = treeNodes.stream().collect(Collectors.toMap(n -> n.getNode().getId(), n -> n));
        this.parentIdMap = treeNodes.stream().collect(Collectors.groupingBy(n -> n.getNode().getParentId()));
    }

    public static <T extends Node> Tree<T> build(List<T> nodes) {
        List<TreeNode<T>> treeNodes = nodes.stream().filter(n -> n.getParentId() != null).map(n -> new TreeNode<>(n, new ArrayList<>())).collect(Collectors.toList());
        Map<Serializable, List<TreeNode<T>>> parentIdMap =
                treeNodes.stream().collect(Collectors.groupingBy(n -> n.getNode().getParentId()));
        treeNodes.forEach(n -> {
            List<TreeNode<T>> children = Optional.ofNullable(parentIdMap.get(n.getNode().getId())).orElse(Collections.emptyList());
            children.sort(Comparator.comparing(TreeNode::getNode));
            n.setChildren(children);
        });
        return new Tree<>(treeNodes);
    }


    public TreeNode<T> get(Serializable nodeId) {
        return this.nodeMap.get(nodeId);
    }

    public List<TreeNode<T>> getByParentId(Serializable nodeId) {
        return this.parentIdMap.get(nodeId);
    }


    public interface Node extends Comparable<Node> {
        Serializable getId();

        Serializable getParentId();
    }

    @Data
    public static class TreeNode<T extends Node> {
        @JsonUnwrapped
        private final T node;
        private List<TreeNode<T>> children;

        public TreeNode(T node, List<TreeNode<T>> children) {
            this.node = node;
            this.children = children;
        }

    }

}
