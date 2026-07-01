package com.kiwi.project.bpm.model;

import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@Document("bpmTemplatePack")
public class BpmTemplatePack extends BaseEntity<String> {

    public enum Kind {
        Single,
        Solution
    }

    public enum Status {
        Draft,
        PendingReview,
        Published,
        Deprecated
    }

    public enum Visibility {
        Private,
        Org,
        Public
    }

    private String name;
    @Indexed(unique = true, sparse = true)
    private String slug;
    private String summary;
    private String readme;
    private List<String> tags = new ArrayList<>();
    private String category;
    private Kind kind;
    private BpmTemplatePackManifest manifest;
    private int processCount;
    private List<String> entryProcessKeys = new ArrayList<>();
    private String publisherId;
    private String publisherOrg;
    private String version;
    private String changelog;
    private Status status;
    private Visibility visibility;
    private String checksum;
    private String signature;
    private long installCount;
    private String previewImageUrl;
}
