package com.cryo.common.mongo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;
import java.util.List;

/**
 * Created by wanghong on 2017/9/21
 */
@NoRepositoryBean  //避免spring扫描BaseRepository
public interface BaseRepository<T, ID extends Serializable> extends MongoRepository<T, ID> {
    Page<T> findByQuery(Query query, Pageable pageable);

    List<T> findByQuery(Query query);
    long countByQuery(Query query);

    Page<T> findByParams(Object params, Pageable pageable);

    void update(Update update, Query query);
}