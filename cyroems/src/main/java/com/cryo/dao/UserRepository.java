package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.user.User;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserRepository extends BaseRepository<User, String>
{
    @Query("{ 'email' : ?0 }")
    Optional<User> findByEmail(String email);


}
