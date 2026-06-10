package com.kiwi.project.bpm.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.bpm.model.BpmInboundRegistration;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BpmInboundRegistrationDao extends BaseMongoRepository<BpmInboundRegistration, String> {

    Optional<BpmInboundRegistration> findByComponentKey(String componentKey);
}
