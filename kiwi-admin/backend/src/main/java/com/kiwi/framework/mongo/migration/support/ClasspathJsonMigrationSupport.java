package com.kiwi.framework.mongo.migration.support;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwi.common.entity.IdEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClasspathJsonMigrationSupport {

    private final ObjectMapper objectMapper;

    public <T extends IdEntity<String>> void loadAndUpsert(
            String classpathLocation,
            Class<T> entityType,
            MongoRepository<T, String> repository) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            log.info("Mongo migration JSON not found, skip: {}", classpathLocation);
            return;
        }
        loadAndUpsert(resource, entityType, repository);
    }

    public <T extends IdEntity<String>> void loadAndUpsert(
            Resource resource, Class<T> entityType, MongoRepository<T, String> repository) {
        String locationLabel = resource.getDescription();
        List<T> entities = readEntities(locationLabel, resource, entityType);
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
        log.info("Mongo migration upserted {} {} from {}", saved, entityType.getSimpleName(), locationLabel);
    }

    private <T> List<T> readEntities(String locationLabel, Resource resource, Class<T> entityType) {
        try (InputStream inputStream = resource.getInputStream()) {
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, entityType);
            return objectMapper.readValue(inputStream, listType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read migration JSON: " + locationLabel, ex);
        }
    }
}
