package com.kiwi.project.system.dao;

import com.kiwi.common.mongo.BaseMongoRepository;
import com.kiwi.project.system.entity.SysDept;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface SysDeptDao extends BaseMongoRepository<SysDept, String>
{
    /**
     * 根据父部门ID查询子部门
     *
     * @param id 父部门ID
     * @return 子部门
     */
    @Query("{parentId: ?0}")
    List<SysDept> findByParentId(String id);
}
