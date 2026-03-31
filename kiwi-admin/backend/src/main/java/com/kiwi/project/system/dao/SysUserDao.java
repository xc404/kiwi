package com.kiwi.project.system.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.system.entity.SysUser;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface SysUserDao extends BaseMongoRepository<SysUser, String>
{
    @Query("{ 'userName' : ?0 }")
    Optional<SysUser> findByUsername(String email);
}
