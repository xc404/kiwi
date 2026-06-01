package com.kiwi.framework.mongo.migration.json;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MongoJsonMigrationRunner implements ApplicationRunner {

    private final MongoJsonMigrationExecutor migrationExecutor;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running classpath JSON migrations (versioned + repeatable)");
        migrationExecutor.migrateAll();
    }
}
