package com.kiwi.project.system.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.system.entity.SysDict;
import org.springframework.data.mongodb.repository.DeleteQuery;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface SysDictDao extends BaseMongoRepository<SysDict, String>
{
    @Query("{ 'groupCode' : ?0 }")
    List<SysDict> findByGroup(String groupCode);

    @DeleteQuery("{ 'groupKey' : ?0 }")
    void deleteByGroup(String groupKey);
}
