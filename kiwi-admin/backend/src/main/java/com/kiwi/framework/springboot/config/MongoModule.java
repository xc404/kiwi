package com.kiwi.framework.springboot.config;

import com.kiwi.common.mongo.BaseMongoRepositoryImpl;
import com.kiwi.common.mongo.KiwiMongoTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableMongoRepositories(basePackages = "com.kiwi", excludeFilters = {
        @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = CryoemsMongoModule.REPOSITORY_SCAN_EXCLUDE_REGEX)
},
        repositoryBaseClass = BaseMongoRepositoryImpl.class, mongoTemplateRef = "mongoTemplate")
@Configuration()
@Slf4j
public class MongoModule
{

    @Primary
    @Bean("mongoTemplate")
    public KiwiMongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory, MongoConverter converter) {
        return new KiwiMongoTemplate(mongoDatabaseFactory, converter);
    }

    /** 主库连接属性；{@code @Primary} 避免与 {@link CryoemsMongoModule} 的 cryoems 库冲突。 */
    @Primary
    @Bean("primaryMongoProperties")
    @ConfigurationProperties(prefix = "spring.data.mongodb")
    public MongoProperties primaryMongoProperties() {
        return new MongoProperties();
    }

}