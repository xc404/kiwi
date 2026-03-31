package com.kiwi.project.bpm.model;

import lombok.Data;

@Data
public class BpmComponentParameter
{
    private String key;

    private String name;

    private String description;

    private String defaultValue;

    private boolean array;

    private boolean required;

    private boolean readonly;

    private boolean hidden;

    private String htmlType;

    private String type;

    private String example;

    private String dictKey;


    private String group;

    private boolean important;
}
