package com.kiwi.common.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Tree<T extends Node>
{

    private final Map<Serializable, List<TreeNode<T>>> parentIdMap;
    private final Map<Serializable, TreeNode<T>> nodeMap;
    private final List<TreeNode<T>> rootNodes;

    private Tree(List<TreeNode<T>> treeNodes) {
        this.nodeMap = treeNodes.stream().collect(Collectors.toMap(n -> n.getNode().getId(), n -> n));
        this.parentIdMap = treeNodes.stream().collect(Collectors.groupingBy(n -> n.getNode().getParentId()));
        this.rootNodes = new ArrayList<>();
        this.parentIdMap.keySet().stream().filter(p -> !this.nodeMap.containsKey(p)).forEach(p -> {
            List<TreeNode<T>> children = this.parentIdMap.get(p);
            children.sort(Comparator.comparing(c -> c.getNode().getSort()));
            this.rootNodes.addAll(children);
        });
    }

    public static <T extends Node> Tree<T> build(List<T> nodes) {
        List<TreeNode<T>> treeNodes = nodes.stream().filter(n -> n.getParentId() != null).map(n -> new TreeNode<>(n, new ArrayList<>())).collect(Collectors.toList());
        Map<Serializable, List<TreeNode<T>>> parentIdMap =
                treeNodes.stream().collect(Collectors.groupingBy(n -> n.getNode().getParentId()));
        treeNodes.forEach(n -> {
            List<TreeNode<T>> children = Optional.ofNullable(parentIdMap.get(n.getNode().getId())).orElse(Collections.emptyList());
            children.sort(Comparator.comparing(c -> c.getNode().getSort()));
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


    public List<TreeNode<T>> getDefaultRoot(){
      return   getByParentId(Node.ROOT_ID);
    }

    public List<TreeNode<T>> getRootNodes() {
        return this.rootNodes;
    }
}
