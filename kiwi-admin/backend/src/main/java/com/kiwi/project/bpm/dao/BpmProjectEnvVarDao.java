package com.kiwi.project.bpm.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmProjectEnvVar;
import org.springframework.stereotype.Repository;

@Repository
public interface BpmProjectEnvVarDao extends BaseMongoRepository<BpmProjectEnvVar, String> {
}
