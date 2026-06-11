package com.kiwi.framework.mongo;

import com.kiwi.framework.mongo.migration.MongoInitAdminProperties;
import com.kiwi.framework.mongo.migration.json.MongoJsonMigrationProperties;
import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableMongock
@EnableConfigurationProperties({MongoJsonMigrationProperties.class, MongoInitAdminProperties.class})
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MongoMigrationConfiguration {
}
