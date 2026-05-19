package com.kiwi.framework.security.satoken;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.util.SaFoxUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 使用主库 MongoDB（{@code mongoTemplate}）持久化 Sa-Token 会话。
 */
public class SaTokenMongoDao implements SaTokenDao {

    private final MongoTemplate mongoTemplate;

    public SaTokenMongoDao(@Qualifier("mongoTemplate") MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // ------------------------ String 读写操作

    @Override
    public String get(String key) {
        return findLiveEntry(key).map(SaTokenCacheEntry::getValue).orElse(null);
    }

    @Override
    public void set(String key, String value, long timeout) {
        upsertByPath(key, "value", value, timeout);
    }

    @Override
    public void update(String key, String value) {
        updateByPath(key, "value", value);
    }

    @Override
    public void delete(String key) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(key)), SaTokenCacheEntry.class);
    }

    @Override
    public long getTimeout(String key) {
        SaTokenCacheEntry entry = mongoTemplate.findById(key, SaTokenCacheEntry.class);
        if (entry == null) {
            return SaTokenDao.NOT_VALUE_EXPIRE;
        }
        if (entry.getTimeout() == null) {
            return SaTokenDao.NEVER_EXPIRE;
        }
        long remainingSeconds = (entry.getTimeout().getTime() - System.currentTimeMillis()) / 1000;
        if (remainingSeconds < 0) {
            delete(key);
            return SaTokenDao.NOT_VALUE_EXPIRE;
        }
        return remainingSeconds;
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        Update update = new Update();
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            update.unset("timeout");
        } else {
            update.set("timeout", timeoutToDate(timeout));
        }
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(key)),
                update,
                SaTokenCacheEntry.class);
    }

    // ------------------------ Object 读写操作

    @Override
    public Object getObject(String key) {
        return findLiveEntry(key).map(SaTokenCacheEntry::getObject).orElse(null);
    }

    @Override
    public <T> T getObject(String key, Class<T> classType) {
        Object value = getObject(key);
        if (value == null) {
            return null;
        }
        if (classType.isInstance(value)) {
            return classType.cast(value);
        }
        return null;
    }

    @Override
    public void setObject(String key, Object object, long timeout) {
        upsertByPath(key, "object", object, timeout);
    }

    @Override
    public void updateObject(String key, Object object) {
        updateByPath(key, "object", object);
    }

    @Override
    public void deleteObject(String key) {
        delete(key);
    }

    @Override
    public long getObjectTimeout(String key) {
        return getTimeout(key);
    }

    @Override
    public void updateObjectTimeout(String key, long timeout) {
        updateTimeout(key, timeout);
    }

    @Override
    public void updateSessionTimeout(String sessionId, long timeout) {
        updateObjectTimeout(sessionId, timeout);
    }

    // ------------------------ SaSession 读写

    @Override
    public SaSession getSession(String sessionId) {
        return getObject(sessionId, SaSession.class);
    }

    @Override
    public void setSession(SaSession session, long timeout) {
        setObject(session.getId(), session, timeout);
    }

    @Override
    public void updateSession(SaSession session) {
        updateObject(session.getId(), session);
    }

    @Override
    public void deleteSession(String sessionId) {
        deleteObject(sessionId);
    }

    @Override
    public long getSessionTimeout(String sessionId) {
        return getObjectTimeout(sessionId);
    }

    // ------------------------ 搜索

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        Date now = new Date();
        List<SaTokenCacheEntry> entries = mongoTemplate.find(
                Query.query(Criteria.where("_id")
                        .regex(prefix + ".*" + keyword + ".*")
                        .and("timeout").gte(now)),
                SaTokenCacheEntry.class);
        List<String> values = entries.stream()
                .map(SaTokenCacheEntry::getValue)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return SaFoxUtil.searchList(values, start, size, sortType);
    }

    private Optional<SaTokenCacheEntry> findLiveEntry(String key) {
        SaTokenCacheEntry entry = mongoTemplate.findById(key, SaTokenCacheEntry.class);
        if (entry == null || !entry.isLive()) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    private Date timeoutToDate(long timeoutSeconds) {
        return new Date(timeoutSeconds * 1000 + System.currentTimeMillis());
    }

    private void upsertByPath(String key, String path, Object value, long timeout) {
        if (timeout == 0 || timeout <= SaTokenDao.NOT_VALUE_EXPIRE) {
            return;
        }
        Update update = Update.update(path, value);
        if (timeout != SaTokenDao.NEVER_EXPIRE) {
            update.set("timeout", timeoutToDate(timeout));
        } else {
            update.unset("timeout");
        }
        mongoTemplate.upsert(
                Query.query(Criteria.where("_id").is(key)),
                update,
                SaTokenCacheEntry.class);
    }

    private void updateByPath(String key, String path, Object value) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(key).and("timeout").gte(new Date())),
                Update.update(path, value),
                SaTokenCacheEntry.class);
    }
}
