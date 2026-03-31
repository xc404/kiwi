package com.kiwi.project.bpm.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmProject;
import org.springframework.stereotype.Repository;

@Repository
public interface BpmProjectDao extends BaseMongoRepository<BpmProject,String>
{
}
