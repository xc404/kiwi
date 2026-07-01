package com.kiwi.project.bpm.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmTemplateProcess;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BpmTemplateProcessDao extends BaseMongoRepository<BpmTemplateProcess, String> {

    List<BpmTemplateProcess> findByPackIdOrderBySortAsc(String packId);

    Optional<BpmTemplateProcess> findByPackIdAndProcessKey(String packId, String processKey);

    void deleteByPackId(String packId);
}
