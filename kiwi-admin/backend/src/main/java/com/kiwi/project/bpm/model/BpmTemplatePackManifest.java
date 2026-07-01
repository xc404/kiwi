package com.kiwi.project.bpm.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BpmTemplatePackManifest {

    private String kiwiMinVersion = "1.0.0";
    private String kind;
    private List<String> requiredComponentKeys = new ArrayList<>();
    private List<String> processKeys = new ArrayList<>();
    private List<String> entryProcessKeys = new ArrayList<>();
    private List<CallActivityBinding> callActivityBindings = new ArrayList<>();

    @Data
    public static class CallActivityBinding {
        private String callerProcessKey;
        private String activityId;
        private String calleeProcessKey;
    }
}
