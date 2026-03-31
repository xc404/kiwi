package com.kiwi.project.system.spi;

import com.kiwi.common.tree.TreeNode;
import com.kiwi.common.tree.Node;

import java.util.List;
import java.util.Map;

public interface TreeProvider<T extends Node>
{
    String group();

    List<T> getChildren(String parentId, Map<String, Object> params);

    List<TreeNode<T>> getTree(String parentId, Map<String, Object> params);
}
