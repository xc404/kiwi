package com.kiwi.project.bpm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BpmRemoteMarketIndex {

    private int schemaVersion;
    private String generatedAt;
    private List<BpmRemoteMarketIndexItem> items = new ArrayList<>();
}
