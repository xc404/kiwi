package com.kiwi.common.mongo;

import com.kiwi.common.entity.IdEntity;
import com.kiwi.common.query.QueryParams;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

@NoRepositoryBean  //避免spring扫描BaseRepository
public interface BaseMongoRepository<T extends IdEntity<ID>, ID> extends MongoRepository<T, ID>
{
    Page<T> findBy(Query query, Pageable pageable);

    List<T> findBy(Query query);

    long countBy(Query query);


    List<T> findBy(QueryParams params);

    Page<T> findBy(QueryParams queryParams, Pageable pageable);

    void update(Update update, Query query);

    T updateSelective(T entity);
}