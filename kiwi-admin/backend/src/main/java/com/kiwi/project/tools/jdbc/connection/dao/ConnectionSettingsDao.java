package com.kiwi.project.tools.jdbc.connection.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.tools.jdbc.connection.entity.ConnectionSettings;

public interface ConnectionSettingsDao extends BaseMongoRepository<ConnectionSettings, String>
{
}
