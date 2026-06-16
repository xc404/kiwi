package com.kiwi.framework.mongo.migration.support;

import com.kiwi.common.entity.IdEntity;

import java.util.List;

public record JsonMigrationPayload<T extends IdEntity<String>>(Class<T> entityType, List<T> records) {}
