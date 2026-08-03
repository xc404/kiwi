package com.kiwi.project.bpm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kiwi.project.bpm.dto.BpmRemoteMarketItemDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BpmRemoteMarketIndexItem {

    private String type;
    private String slug;
    private String name;
    private String version;
    private String summary;
    private String category;
    private List<String> tags = new ArrayList<>();
    private String kiwiMinVersion;
    private String downloadUrl;
    private String sha256;
    private String manifestUrl;
    private String signatureUrl;
    private String kind;
    private Integer processCount;
    private List<String> requiredComponentKeys = new ArrayList<>();
    private List<String> componentKeys = new ArrayList<>();
    private BpmRemoteMarketItemDto.MavenCoordinate mavenCoordinate;
}
