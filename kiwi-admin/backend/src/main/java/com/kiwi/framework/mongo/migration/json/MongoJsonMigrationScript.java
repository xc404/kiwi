package com.kiwi.framework.mongo.migration.json;

import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
public class MongoJsonMigrationScript {

    private final String scriptKey;
    private final MongoJsonMigrationType type;
    private final String version;
    private final String entitySimpleName;
    private final Resource resource;

    public MongoJsonMigrationScript(
            String scriptKey,
            MongoJsonMigrationType type,
            String version,
            String entitySimpleName,
            Resource resource) {
        this.scriptKey = scriptKey;
        this.type = type;
        this.version = version;
        this.entitySimpleName = entitySimpleName;
        this.resource = resource;
    }
}
