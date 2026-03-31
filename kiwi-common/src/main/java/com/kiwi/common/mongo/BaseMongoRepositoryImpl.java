package com.kiwi.common.mongo;

import com.kiwi.common.entity.IdEntity;
import com.kiwi.common.query.QueryParams;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.query.MongoEntityInformation;
import org.springframework.data.mongodb.repository.support.SimpleMongoRepository;

import java.util.List;

public class BaseMongoRepositoryImpl<T extends IdEntity<ID>, ID> extends SimpleMongoRepository<T, ID> implements BaseMongoRepository<T, ID>
{

    protected final KiwiMongoTemplate kiwiMongoTemplate;

    protected final MongoEntityInformation<T, ID> entityInformation;

    private Class<T> clazz;

    public BaseMongoRepositoryImpl(MongoEntityInformation<T, ID> metadata, KiwiMongoTemplate mongoOperations) {
        super(metadata, mongoOperations);
        this.kiwiMongoTemplate = mongoOperations;
        this.entityInformation = metadata;
        clazz = entityInformation.getJavaType();
    }

    @Override
    public List<T> findBy(Query query) {

        return this.kiwiMongoTemplate.find(query, clazz);
    }

    @Override
    public long countBy(Query query) {
        return this.kiwiMongoTemplate.count(query, clazz);
    }

    @Override
    public <S extends T> S save(S entity) {
        return this.kiwiMongoTemplate.save(entity);
    }

    @Override
    public  T updateSelective(T entity) {
        this.kiwiMongoTemplate.updateSelective(entity);
        return entity;
    }

    @Override
    public Page<T> findBy(Query query, Pageable pageable) {
        boolean total = true;
//        if( pageable instanceof RequestTotalPageRequest ) {
//            total = ((RequestTotalPageRequest) pageable).requestTotal();
//        }
        Query countQuery = Query.of(query);
        query.with(pageable);
        List<T> list = this.kiwiMongoTemplate.find(query, clazz);
        long count = list.size();
        if( total ) {
            count = this.kiwiMongoTemplate.count(countQuery, clazz);
        }

        return new PageImpl<>(list, pageable, count);
    }

    @Override
    public Page<T> findBy(QueryParams queryParams, Pageable pageable) {
        return this.findBy(queryParams.toMongo(), pageable);
    }

    @Override
    public List<T> findBy(QueryParams queryParams) {
        return this.findBy(queryParams.toMongo());
    }

    @Override
    public void update(Update update, Query query) {
        this.kiwiMongoTemplate.updateMulti(query, update, clazz);
    }
}
