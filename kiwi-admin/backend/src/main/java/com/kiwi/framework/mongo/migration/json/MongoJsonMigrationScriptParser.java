package com.kiwi.framework.mongo.migration.json;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MongoJsonMigrationScriptParser {

    private static final String VERSIONED_PREFIX = "V";
    private static final String REPEATABLE_PREFIX = "R__";
    private static final String SEPARATOR = "__";

    public Optional<MongoJsonMigrationScript> parse(String scriptKey, MongoJsonMigrationType type, Resource resource) {
        String filename = resource.getFilename();
        if (StringUtils.isBlank(filename) || !filename.endsWith(".json")) {
            return Optional.empty();
        }
        if (type == MongoJsonMigrationType.VERSIONED) {
            return parseVersioned(scriptKey, filename, resource);
        }
        return parseRepeatable(scriptKey, filename, resource);
    }

    private Optional<MongoJsonMigrationScript> parseVersioned(
            String scriptKey, String filename, Resource resource) {
        if (!filename.startsWith(VERSIONED_PREFIX) || !filename.contains(SEPARATOR)) {
            return Optional.empty();
        }
        int separatorIndex = filename.indexOf(SEPARATOR);
        String version = filename.substring(VERSIONED_PREFIX.length(), separatorIndex);
        String entitySimpleName = stripJsonSuffix(filename.substring(separatorIndex + SEPARATOR.length()));
        if (StringUtils.isAnyBlank(version, entitySimpleName)) {
            return Optional.empty();
        }
        return Optional.of(new MongoJsonMigrationScript(
                scriptKey, MongoJsonMigrationType.VERSIONED, version, entitySimpleName, resource));
    }

    private Optional<MongoJsonMigrationScript> parseRepeatable(
            String scriptKey, String filename, Resource resource) {
        if (!filename.startsWith(REPEATABLE_PREFIX)) {
            return Optional.empty();
        }
        String entitySimpleName = stripJsonSuffix(filename.substring(REPEATABLE_PREFIX.length()));
        if (StringUtils.isBlank(entitySimpleName)) {
            return Optional.empty();
        }
        return Optional.of(new MongoJsonMigrationScript(
                scriptKey, MongoJsonMigrationType.REPEATABLE, null, entitySimpleName, resource));
    }

    private String stripJsonSuffix(String name) {
        if (name.endsWith(".json")) {
            return name.substring(0, name.length() - ".json".length());
        }
        return name;
    }
}
