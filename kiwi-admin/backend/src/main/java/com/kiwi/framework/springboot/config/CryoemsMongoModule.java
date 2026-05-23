package com.kiwi.framework.springboot.config;

import com.kiwi.common.mongo.BaseMongoRepositoryImpl;
import com.kiwi.common.mongo.KiwiMongoTemplate;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.mongo.MongoConnectionDetails;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.PropertiesMongoConnectionDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * CryoEMS 业务库（与 cryo-web-server {@code dataset.mongodb} 同源），供 {@code MovieResultRepository} 等访问。
 */
@EnableMongoRepositories(
        basePackages = CryoemsMongoModule.REPOSITORY_BASE_PACKAGE,
        repositoryBaseClass = BaseMongoRepositoryImpl.class,
        mongoTemplateRef = "cryoemsMongoTemplate")
@Configuration
@Slf4j
public class CryoemsMongoModule {

    /**
     * CryoEMS 业务库 Repository 扫描包，供 {@link MongoModule} exclude 复用。
     *
     * <p>历史路径为 {@code com.kiwi.cryoems.bpm.dao}；按 movie / mdoc 拆包后实际位于
     * {@code com.kiwi.cryoems.bpm.movie.dao}（以及未来的 {@code com.kiwi.cryoems.bpm.mdoc.dao}），
     * 这里改用更宽的根包 {@code com.kiwi.cryoems.bpm} 让 Spring Data 自动发现所有 cryoems 子包下的 Repository。</p>
     */
    public static final String REPOSITORY_BASE_PACKAGE = "com.kiwi.cryoems.bpm";

    /** 主库 {@link MongoModule} 排除 CryoEMS Repository 用的正则（无需 AspectJ）。 */
    public static final String REPOSITORY_SCAN_EXCLUDE_REGEX =
            "com\\.kiwi\\.cryoems\\.bpm\\..*";

    @Bean("cryoemsMongoTemplate")
    public KiwiMongoTemplate cryoemsMongoTemplate(
            @Qualifier("cryoemsMongoProperties") MongoProperties properties,
            ObjectProvider<SslBundles> sslBundles,
            MongoConverter converter) {
        MongoConnectionDetails connectionDetails = new PropertiesMongoConnectionDetails(properties,sslBundles.getIfAvailable());
        MongoClient mongoClient = MongoClients.create(connectionDetails.getConnectionString());
        SimpleMongoClientDatabaseFactory factory =
                new SimpleMongoClientDatabaseFactory(mongoClient, properties.getDatabase());
        return new KiwiMongoTemplate(factory, converter);
    }

    @Bean("cryoemsMongoProperties")
    @ConfigurationProperties(prefix = "cryoems.mongodb")
    public MongoProperties cryoemsMongoProperties() {
        log.info("cryoems MongoDB properties bean created");
        return new MongoProperties();
    }
}
