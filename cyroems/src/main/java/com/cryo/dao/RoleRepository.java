package com.cryo.dao;

import com.cryo.common.mongo.BaseRepository;
import com.cryo.model.user.Role;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface RoleRepository extends BaseRepository<Role, String>
{
    @Query("{ 'role_name': ?0 }")
    Optional<Role> findByRoleName(String roleName);

    @Query("{ 'role_id': ?0 }")
    Optional<Role> findByRoleId(Integer roleId);
}
