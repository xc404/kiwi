package com.kiwi.framework.permission;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class Permission
{
    private String key;
    private String description;
    private Set<String> requiredByModules;

    public void add(String module) {
        if(requiredByModules == null){
            requiredByModules = new HashSet<>();
        }
        requiredByModules.add(module);
    }

}
