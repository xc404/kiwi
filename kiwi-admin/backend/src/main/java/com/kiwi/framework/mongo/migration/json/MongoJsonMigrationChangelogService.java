package com.kiwi.framework.mongo.migration.json;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MongoJsonMigrationChangelogService {

    private final MongoTemplate mongoTemplate;
    private final MongoJsonMigrationProperties properties;

    public Optional<MongoJsonMigrationRecord> findByScript(String script) {
        Query query = Query.query(Criteria.where("_id").is(script));
        MongoJsonMigrationRecord record = mongoTemplate.findOne(
                query, MongoJsonMigrationRecord.class, properties.getChangelogCollection());
        return Optional.ofNullable(record);
    }

    public void recordSuccess(MongoJsonMigrationScript script, String checksum) {
        MongoJsonMigrationRecord record = new MongoJsonMigrationRecord();
        record.setScript(script.getScriptKey());
        record.setType(script.getType());
        record.setChecksum(checksum);
        record.setInstalledOn(Instant.now());
        record.setSuccess(true);
        mongoTemplate.save(record, properties.getChangelogCollection());
    }
}
