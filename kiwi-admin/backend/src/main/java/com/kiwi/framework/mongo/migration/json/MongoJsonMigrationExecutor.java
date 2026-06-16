package com.kiwi.framework.mongo.migration.json;

import com.kiwi.common.entity.IdEntity;
import com.kiwi.framework.mongo.migration.support.ClasspathJsonMigrationSupport;
import com.kiwi.framework.mongo.migration.support.JsonMigrationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MongoJsonMigrationExecutor {

    private static final String VERSIONED_FOLDER = "versioned";
    private static final String REPEATABLE_FOLDER = "repeatable";

    private final MongoJsonMigrationProperties properties;
    private final MongoJsonMigrationScriptParser scriptParser;
    private final MongoJsonMigrationChangelogService changelogService;
    private final MongoRepositoryResolver repositoryResolver;
    private final ClasspathJsonMigrationSupport jsonMigrationSupport;
    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    public void migrateAll() {
        List<MongoJsonMigrationScript> versioned = loadScripts(
                properties.getVersionedLocation(), VERSIONED_FOLDER, MongoJsonMigrationType.VERSIONED);
        versioned.sort(Comparator.comparing(MongoJsonMigrationScript::getVersion));
        for (MongoJsonMigrationScript script : versioned) {
            migrateVersioned(script);
        }

        List<MongoJsonMigrationScript> repeatable = loadScripts(
                properties.getRepeatableLocation(), REPEATABLE_FOLDER, MongoJsonMigrationType.REPEATABLE);
        repeatable.sort(Comparator.comparing(MongoJsonMigrationScript::getScriptKey));
        for (MongoJsonMigrationScript script : repeatable) {
            migrateRepeatable(script);
        }
    }

    private void migrateVersioned(MongoJsonMigrationScript script) {
        if (changelogService.findByScript(script.getScriptKey()).isPresent()) {
            log.debug("Skip versioned JSON migration, already applied: {}", script.getScriptKey());
            return;
        }
        applyScript(script);
    }

    private void migrateRepeatable(MongoJsonMigrationScript script) {
        String checksum = checksum(script.getResource());
        Optional<MongoJsonMigrationRecord> existing = changelogService.findByScript(script.getScriptKey());
        if (existing.isPresent() && checksum.equals(existing.get().getChecksum())) {
            log.debug("Skip repeatable JSON migration, checksum unchanged: {}", script.getScriptKey());
            return;
        }
        applyScript(script);
    }

    private void applyScript(MongoJsonMigrationScript script) {
        String checksum = checksum(script.getResource());
        applyPayload(
                script,
                checksum,
                jsonMigrationSupport.readPayload(
                        script.getResource(),
                        script.getEntitySimpleName(),
                        properties.getEntityBasePackage()));
    }

    private <T extends IdEntity<String>> void applyPayload(
            MongoJsonMigrationScript script, String checksum, JsonMigrationPayload<T> payload) {
        MongoRepository<T, String> repository = repositoryResolver.resolve(payload.entityType());
        jsonMigrationSupport.upsert(script.getResource().getDescription(), payload, repository);
        changelogService.recordSuccess(script, checksum);
        log.info(
                "Applied JSON migration {} ({})",
                script.getScriptKey(),
                payload.entityType().getName());
    }

    private List<MongoJsonMigrationScript> loadScripts(
            String location, String folderKey, MongoJsonMigrationType type) {
        List<MongoJsonMigrationScript> scripts = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources(toSearchPattern(location));
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                String filename = resource.getFilename();
                if (StringUtils.isBlank(filename)) {
                    continue;
                }
                String scriptKey = folderKey + "/" + filename;
                scriptParser.parse(scriptKey, type, resource).ifPresent(scripts::add);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to scan JSON migrations at " + location, ex);
        }
        return scripts;
    }

    private String toSearchPattern(String location) {
        String path = location;
        if (path.startsWith("classpath:")) {
            path = "classpath*:" + path.substring("classpath:".length());
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return path + "**/*.json";
    }

    private String checksum(Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to checksum migration resource: " + resource, ex);
        }
    }
}
