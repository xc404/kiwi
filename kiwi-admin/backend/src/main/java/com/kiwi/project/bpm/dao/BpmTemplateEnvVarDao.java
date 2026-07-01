package com.kiwi.project.bpm.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmTemplateEnvVar;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BpmTemplateEnvVarDao extends BaseMongoRepository<BpmTemplateEnvVar, String> {

    List<BpmTemplateEnvVar> findByPackIdOrderBySortAscKeyAsc(String packId);

    void deleteByPackId(String packId);
}
