package com.kiwi.framework.mongo.migration.json;

import com.kiwi.common.entity.IdEntity;
import com.kiwi.common.mongo.BaseMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "kiwi.mongodb.migration",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MongoRepositoryResolver {

    private final ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    public <T extends IdEntity<String>> MongoRepository<T, String> resolve(Class<T> entityType) {
        String[] beanNames = applicationContext.getBeanNamesForType(MongoRepository.class);
        for (String beanName : beanNames) {
            Class<?> repositoryType = applicationContext.getType(beanName);
            if (repositoryType == null) {
                continue;
            }
            Class<?> domainType = resolveDomainType(repositoryType);
            if (domainType != null && domainType.equals(entityType)) {
                return (MongoRepository<T, String>) applicationContext.getBean(beanName, MongoRepository.class);
            }
        }
        throw new IllegalStateException(
                "No MongoRepository found for entity " + entityType.getName()
                        + "; ensure a Dao extends BaseMongoRepository for this entity");
    }

    private Class<?> resolveDomainType(Class<?> repositoryType) {
        Class<?> userType = ClassUtils.getUserClass(repositoryType);
        ResolvableType type = ResolvableType.forClass(userType).as(BaseMongoRepository.class);
        if (type.resolve() == null) {
            type = ResolvableType.forClass(userType).as(MongoRepository.class);
        }
        return type.getGeneric(0).resolve();
    }
}
