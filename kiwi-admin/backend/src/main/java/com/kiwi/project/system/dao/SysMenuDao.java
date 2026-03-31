package com.kiwi.project.system.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.system.entity.SysMenu;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface SysMenuDao extends BaseMongoRepository<SysMenu, String>
{
    @Query("{parentId:'?0'}")
    List<SysMenu> findByParentId(String parentId);
}
