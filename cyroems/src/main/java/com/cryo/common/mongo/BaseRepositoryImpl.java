package com.cryo.common.mongo;

import com.cryo.common.query.QueryParams;
import com.cryo.common.query.TotalPageRequest;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;

import java.io.Serializable;
import java.util.List;

/**
 * @author wanghong
 * @desc
 * @date: 2017/9/21  10:40
 * @Copyright (c) 2017, DaChen All Rights Reserved.
 */
public class BaseRepositoryImpl<T, ID extends Serializable> extends SimpleMongoRepository<T, ID> implements BaseRepository<T, ID>
{

    protected final MongoTemplate mongoTemplate;

    protected final MongoEntityInformation<T, ID> entityInformation;

    private Class<T> clazz;

    public BaseRepositoryImpl(MongoEntityInformation<T, ID> metadata, MongoTemplate mongoOperations) {
        super(metadata, mongoOperations);
        this.mongoTemplate = mongoOperations;
        this.entityInformation = metadata;
        clazz = entityInformation.getJavaType();
    }

    @Override
    public List<T> findByQuery(Query query) {

        return this.mongoTemplate.find(query, clazz);
    }

    @Override
    public long countByQuery(Query query) {
        return this.mongoTemplate.count(query, clazz);
    }

    @Override
    public <S extends T> S save(S entity) {
        return this.mongoTemplate.save(entity);
    }

    @Override
    public Page<T> findByQuery(Query query, Pageable pageable) {
        boolean total = true;
        if( pageable instanceof TotalPageRequest ) {
            total = ((TotalPageRequest) pageable).requestTotal();
        }
        Query countQuery = Query.of(query);
        query.with(pageable);
        List<T> list = this.mongoTemplate.find(query, clazz);
        long count = list.size();
        if( total ) {
            countQuery.limit(Limit.unlimited());
            countQuery.skip(0);
            count = this.mongoTemplate.count(countQuery, clazz);
        }

        return new PageImpl<>(list, pageable, count);
    }

    @Override
    public Page<T> findByParams(Object params, Pageable pageable) {
        Query mongo = QueryParams.from(params).toMongo();
        return this.findByQuery(mongo, pageable);
    }

    @Override
    public void update(Update update, Query query) {
        this.mongoTemplate.updateMulti(query, update, clazz);
    }
}
