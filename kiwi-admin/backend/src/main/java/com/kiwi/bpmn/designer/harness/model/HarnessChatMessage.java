package com.kiwi.bpmn.designer.harness.model;

import lombok.Data;

import java.util.Date;

@Data
public class HarnessChatMessage {
    private String id;
    private String role;
    private String text;
    private Date createdAt;
}