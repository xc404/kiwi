package com.kiwi.framework.mongo;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableMongock
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MongoMigrationConfiguration {
}
