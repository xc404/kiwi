package com.kiwi.project.bpm.dao;


import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmProcess;

public interface BpmProcessDefinitionDao extends BaseMongoRepository<BpmProcess, String>
{
}
