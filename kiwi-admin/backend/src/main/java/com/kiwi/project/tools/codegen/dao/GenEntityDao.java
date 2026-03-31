package com.kiwi.project.tools.codegen.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.tools.codegen.entity.GenEntity;

public interface GenEntityDao extends BaseMongoRepository<GenEntity, String>
{
}
