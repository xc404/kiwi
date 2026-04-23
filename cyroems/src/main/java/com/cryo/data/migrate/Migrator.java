package com.cryo.data.migrate;

import com.cryo.common.model.IdEntity;
import com.cryo.common.mongo.MongoTemplate;
import com.mongodb.DBObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public abstract class Migrator<T>
{
    @Autowired
    private com.cryo.common.mongo.MongoTemplate mongoTemplate;

    @Autowired
    private ConversionService conversionService;

    public void migrate(boolean reset, Query src) {
        if(reset){
            this.reset(src);
        }
        int count = 0;
        Query query = Query.of(src).addCriteria(Criteria.where("migrated").ne(true)).limit(100);
        long total = mongoTemplate.count(Query.of(src).addCriteria(Criteria.where("migrated").ne(true)), getDbClass());
        while( true ) {
            log.info("{} Migrate start, total {} ", getDbClass().toString(), total);
            List<T> result = mongoTemplate.find(query,getDbClass());
            if( result.isEmpty() ) {
                log.info("{} Migrate: {}", getDbClass(), "finished");
                break;
            }
            result.forEach(r -> {
                String id = getId(r);
                try {
                    migrate(r);
                    if( setMigratedOutside()){

                        Update update = new Update();
                        update.set("migrated", true);

                        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)), update, getDbClass());
                    }
                    log.debug("{} Migrate: {}", getDbClass(), id);
                }catch( Exception e ){
                    log.error("{} Migrate: {}", getDbClass(), id, e);
                    throw e;
                }
            });
            count += result.size();
            log.info("{} Migrate: {}, total {}", getDbClass(), count, total);
            if(result.size() < 100){
                log.info("{} Migrate  finished, {}", getDbClass(), total);
                break;
            }
//            break;

        }

    }

    private String getId(T r) {
        if(r instanceof DBObject){
            return ((DBObject) r).get("_id").toString();
        }
        if(r instanceof IdEntity ){
            return ((IdEntity) r).getId();
        }
        throw new RuntimeException("id not found " + JsonUtil.toJson(r));
    }

    public void migrate(boolean reset) {
        this.migrate(reset,new Query());
    }

    abstract void migrate(T r);

    abstract Class getDbClass() ;


    public <T> T getValue(DBObject object, String filedPath, Class<T> requriedType) {
        Map map = object.toMap();
        return getValue(map, filedPath, requriedType);

    }

    protected  <T> T getValue(Map map, String filedPath, Class<T> requriedType) {
        if(map == null){
            return null;
        }
        while( true ) {
            int index = filedPath.indexOf(".");
            if( index < 0 ) {
                break;
            }
            String field = filedPath.substring(0, index);
            map = (Map) map.get(field);
            filedPath = filedPath.substring(index + 1);
        }
        if( !map.containsKey(filedPath) ) {
            return null;
        }
        if( requriedType.isPrimitive() || requriedType.isAssignableFrom(String.class) ) {

            return this.conversionService.convert(map.get(filedPath), requriedType);
        }
        return JsonUtil.convertValue(map.get(filedPath), requriedType);
    }

    public MongoTemplate getMongoTemplate() {
        return mongoTemplate;
    }

    public void setMongoTemplate(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public ConversionService getConversionService() {
        return conversionService;
    }

    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }


    protected void reset(Query query) {
        this.mongoTemplate.updateMulti(Query.of(query).addCriteria(Criteria.where("migrated").is(true)), Update.update("migrated", false), getDbClass());
    }

    protected boolean setMigratedOutside(){
        return false;
    }
}
