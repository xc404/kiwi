package com.kiwi.framework.mongo.migration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.kiwi.common.entity.IdEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ClasspathJsonMigrationSupport {

    private final ObjectMapper objectMapper;

    public <T extends IdEntity<String>> void loadAndUpsert(
            String classpathLocation,
            String filenameEntitySimpleName,
            String defaultEntityBasePackage,
            MongoRepository<T, String> repository) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            log.info("Mongo migration JSON not found, skip: {}", classpathLocation);
            return;
        }
        loadAndUpsert(resource, filenameEntitySimpleName, defaultEntityBasePackage, repository);
    }

    public <T extends IdEntity<String>> void loadAndUpsert(
            Resource resource,
            String filenameEntitySimpleName,
            String defaultEntityBasePackage,
            MongoRepository<T, String> repository) {
        JsonMigrationPayload<T> payload = readPayload(resource, filenameEntitySimpleName, defaultEntityBasePackage);
        upsert(resource.getDescription(), payload, repository);
    }

    public <T extends IdEntity<String>> JsonMigrationPayload<T> readPayload(
            Resource resource, String filenameEntitySimpleName, String defaultEntityBasePackage) {
        String locationLabel = resource.getDescription();
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            String entityClassName = resolveEntityClassName(
                    root, filenameEntitySimpleName, defaultEntityBasePackage, locationLabel);
            Class<T> entityType = resolveEntityType(entityClassName, locationLabel);
            JsonNode recordsNode = recordsNode(root, locationLabel);
            if (recordsNode.isEmpty()) {
                return new JsonMigrationPayload<>(entityType, List.of());
            }
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, entityType);
            List<T> records = objectMapper.readerFor(listType).readValue(recordsNode);
            return new JsonMigrationPayload<>(entityType, records != null ? records : List.of());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read migration JSON: " + locationLabel, ex);
        }
    }

    public <T extends IdEntity<String>> void upsert(
            String locationLabel, JsonMigrationPayload<T> payload, MongoRepository<T, String> repository) {
        List<T> entities = payload.records();
        if (entities.isEmpty()) {
            log.info("Mongo migration JSON empty, skip: {}", locationLabel);
            return;
        }
        int saved = 0;
        for (T entity : entities) {
            if (entity == null || StringUtils.isBlank(entity.getId())) {
                throw new IllegalArgumentException(
                        "Each document in " + locationLabel + " must have a non-blank id");
            }
            repository.save(entity);
            saved++;
        }
        log.info(
                "Mongo migration upserted {} {} from {}",
                saved,
                payload.entityType().getSimpleName(),
                locationLabel);
    }

    private JsonNode recordsNode(JsonNode root, String locationLabel) {
        if (root.isArray()) {
            return root;
        }
        if (!root.isObject()) {
            throw new IllegalStateException(
                    "Migration JSON must be an array or an object with 'entity' and 'records': " + locationLabel);
        }
        JsonNode recordsNode = root.get("records");
        if (recordsNode == null || !recordsNode.isArray()) {
            throw new IllegalStateException(
                    "Migration JSON object must contain array field 'records': " + locationLabel);
        }
        return recordsNode;
    }

    private String resolveEntityClassName(
            JsonNode root, String filenameEntitySimpleName, String defaultEntityBasePackage, String locationLabel) {
        if (root.isArray()) {
            if (StringUtils.isBlank(filenameEntitySimpleName)) {
                throw new IllegalStateException(
                        "Migration JSON array requires entity simple name from filename: " + locationLabel);
            }
            if (StringUtils.isBlank(defaultEntityBasePackage)) {
                throw new IllegalStateException(
                        "Migration JSON array requires kiwi.mongodb.migration.json.entity-base-package: "
                                + locationLabel);
            }
            return defaultEntityBasePackage.trim() + "." + filenameEntitySimpleName.trim();
        }
        if (!root.isObject()) {
            throw new IllegalStateException(
                    "Migration JSON must be an array or an object with 'entity' and 'records': " + locationLabel);
        }
        JsonNode entityNode = root.get("entity");
        if (entityNode == null || !entityNode.isTextual() || StringUtils.isBlank(entityNode.asText())) {
            throw new IllegalStateException(
                    "Migration JSON object must contain non-blank string field 'entity': " + locationLabel);
        }
        return entityNode.asText().trim();
    }

    @SuppressWarnings("unchecked")
    private <T extends IdEntity<String>> Class<T> resolveEntityType(String entityClassName, String locationLabel) {
        try {
            Class<?> type = Class.forName(entityClassName);
            if (!IdEntity.class.isAssignableFrom(type)) {
                throw new IllegalStateException(entityClassName + " does not implement IdEntity");
            }
            return (Class<T>) type;
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "Unknown migration entity class in " + locationLabel + ": " + entityClassName, ex);
        }
    }
}
