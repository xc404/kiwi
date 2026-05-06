package com.kiwi.project.bpm.dao;


import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmComponent;

import java.util.Optional;

public interface BpmComponentDao extends BaseMongoRepository<BpmComponent, String>
{
    /**
     * {@code parentId}、{@code sourceKey} 均与 Mongo 文档一致匹配（含 null 与 null 匹配）。
     */
    Optional<BpmComponent> findFirstByParentIdAndSourceKey(String parentId, String sourceKey);
}
