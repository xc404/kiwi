package com.kiwi.framework.mongo.migration.json;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Getter
@Setter
public class MongoJsonMigrationRecord {

    @Id
    private String script;

    private MongoJsonMigrationType type;

    private String checksum;

    private Instant installedOn;

    private boolean success;
}
