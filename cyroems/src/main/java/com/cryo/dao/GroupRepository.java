package com.cryo.dao;

import com.cryo.model.user.Group;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface GroupRepository extends MongoRepository<Group, String> {
    @Query("{ 'group_name' : ?0 }")
    Optional<Group> findByName(String name);
}
