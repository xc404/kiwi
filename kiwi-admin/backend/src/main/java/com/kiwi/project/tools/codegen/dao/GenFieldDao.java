package com.kiwi.project.tools.codegen.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.tools.codegen.entity.GenField;
import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface GenFieldDao extends BaseMongoRepository<GenField, String>
{
    @DeleteQuery("{ 'entityId': ?0 }")
    void deleteByTableId(String id);

    @Query("{ 'entityId': ?0 }")
    List<GenField> findByTableId(String tableId);
}
