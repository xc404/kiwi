package com.kiwi.project.bpm.dao;


import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmComponent;

import java.util.Optional;

public interface BpmComponentDao extends BaseMongoRepository<BpmComponent, String>
{
    Optional<BpmComponent> findFirstBySourceKey(String sourceKey);
}
