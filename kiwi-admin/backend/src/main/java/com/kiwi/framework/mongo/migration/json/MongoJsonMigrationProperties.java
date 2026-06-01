package com.kiwi.framework.mongo.migration.json;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kiwi.mongodb.migration.json")
public class MongoJsonMigrationProperties {

    private String entityBasePackage = "com.kiwi.project.system.entity";

    private String versionedLocation = "classpath:mongo/migration/versioned/";

    private String repeatableLocation = "classpath:mongo/migration/repeatable/";

    private String changelogCollection = "kiwi_json_migration";
}
