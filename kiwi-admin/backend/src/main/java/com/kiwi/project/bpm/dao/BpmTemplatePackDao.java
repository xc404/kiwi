package com.kiwi.project.bpm.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmTemplatePack;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BpmTemplatePackDao extends BaseMongoRepository<BpmTemplatePack, String> {

    Optional<BpmTemplatePack> findBySlug(String slug);
}
