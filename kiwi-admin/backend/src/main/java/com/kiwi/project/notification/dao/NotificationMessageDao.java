package com.kiwi.project.notification.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.notification.model.NotificationMessage;

import java.util.List;

public interface NotificationMessageDao extends BaseMongoRepository<NotificationMessage, String> {

    long countByUserId(String userId);

    List<NotificationMessage> findByUserIdOrderByCreatedTimeDesc(String userId);
}
