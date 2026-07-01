package com.kiwi.project.bpm.dto;

import com.kiwi.project.bpm.model.BpmTemplatePack;
import lombok.Data;

import java.util.List;

@Data
public class PublishTemplatePackInput {

    private String name;
    private String slug;
    private String summary;
    private String readme;
    private List<String> tags;
    private String category;
    private String version;
    private BpmTemplatePack.Visibility visibility;
}
