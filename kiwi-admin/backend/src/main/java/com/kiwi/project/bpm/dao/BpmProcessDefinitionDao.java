package com.kiwi.project.bpm.dao;


import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmProcess;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BpmProcessDefinitionDao extends BaseMongoRepository<BpmProcess, String>
{
    /**
     * 当前用户保存过的流程，按更新时间从新到旧（用于按需解析「最近使用的组件」）。
     */
    List<BpmProcess> findByCreatedByOrderByUpdatedTimeDesc(String createdBy, Pageable pageable);
}
