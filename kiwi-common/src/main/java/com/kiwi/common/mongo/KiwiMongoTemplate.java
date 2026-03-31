package com.kiwi.common.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;


/**
 * Created by Administrator on 2017/12/18.
 */
public class KiwiMongoTemplate extends org.springframework.data.mongodb.core.MongoTemplate
{
    public static final String ID_FIELD = "_id";

    public KiwiMongoTemplate(MongoClient mongoClient, String databaseName) {
        super(mongoClient, databaseName);
    }

    public KiwiMongoTemplate(MongoDatabaseFactory mongoDbFactory) {
        super(mongoDbFactory);
    }

    public KiwiMongoTemplate(MongoDatabaseFactory mongoDbFactory, @Nullable MongoConverter mongoConverter) {
        super(mongoDbFactory, mongoConverter);
    }

    public <T> T updateSelective(T entity) {
        Pair<String, Object> pair = extractIdPropertyAndValue(entity);
        if( pair.getSecond() == null ) {
            return super.save(entity);
        }
        String collectionName = super.getCollectionName(entity.getClass());
        entity = maybeEmitEvent(new BeforeConvertEvent<>(entity, collectionName)).getSource();
        entity = maybeCallBeforeConvert(entity, collectionName);
        super.maybeEmitEvent(new BeforeConvertEvent<>(entity, collectionName));
        Document dbDoc = new Document();
        getConverter().write(entity, dbDoc);
        dbDoc.remove(pair.getFirst());
        super.maybeEmitEvent(new BeforeSaveEvent<>(entity, dbDoc, collectionName));
        maybeCallBeforeSave(entity, dbDoc, collectionName);
        Update update = new Update();
        dbDoc.forEach(update::set);
        UpdateResult updateResult = this.updateFirst(Query.query(Criteria.where(pair.getFirst()).is(pair.getSecond())),
                update, entity.getClass());
        maybeEmitEvent(new AfterSaveEvent<>(entity, dbDoc, collectionName));
        maybeCallAfterSave(entity, dbDoc, collectionName);


        return entity;
    }


    public Pair<String, Object> extractIdPropertyAndValue(Object object) {

        Assert.notNull(object, "Id cannot be extracted from 'null'.");

        Class<?> objectType = object.getClass();

        if( object instanceof Document ) {
            return Pair.of(ID_FIELD, ((Document) object).get(ID_FIELD));
        }

        MongoPersistentEntity<?> entity = super.getConverter().getMappingContext().getPersistentEntity(objectType);

        if( entity != null && entity.hasIdProperty() ) {

            MongoPersistentProperty idProperty = entity.getIdProperty();
            return Pair.of(idProperty.getFieldName(), entity.getPropertyAccessor(object).getProperty(idProperty));
        }

        throw new MappingException("No id property found for object of type " + objectType);
    }


    public static final class Pair<S, T>
    {

        private final S first;
        private final T second;

        public Pair(S first, T second) {
            this.first = first;
            this.second = second;
        }

        public static <S, T> Pair<S, T> of(S first, T second) {
            return new Pair<>(first, second);
        }

        public static <S, T> Collector<Pair<S, T>, ?, Map<S, T>> toMap() {
            return Collectors.toMap(Pair::getFirst, Pair::getSecond);
        }

        /**
         * Returns the first element of the {@link Pair}.
         *
         * @return
         */
        public S getFirst() {
            return first;
        }

        /**
         * Returns the second element of the {@link Pair}.
         *
         * @return
         */
        public T getSecond() {
            return second;
        }
    }

}
