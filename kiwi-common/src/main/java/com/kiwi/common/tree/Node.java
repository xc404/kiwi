package com.kiwi.common.tree;

import java.io.Serializable;

public interface Node
{

    public static final String ROOT_ID = "0";
    Serializable getId();

    Serializable getParentId();

    String getName();

    Integer getSort();

    default boolean isLeaf(){
        return false;
    }


}
