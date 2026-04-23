package com.cryo.springboot.mongo;

import com.cryo.common.mongo.BaseRepositoryImpl;
import com.cryo.common.mongo.MongoTemplate;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.mongo.MongoConnectionDetails;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.autoconfigure.mongo.PropertiesMongoConnectionDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;


@EnableMongoRepositories(basePackages = "com.cryo.dao.dataset",
        repositoryBaseClass = BaseRepositoryImpl.class, mongoTemplateRef = "datasetMongoTemplate")
@Slf4j
@Configuration
public class DatasetMongoModule
{


    @Bean("datasetMongoTemplate")
    public MongoTemplate datasetMongoTemplate(@Qualifier("datasetMongoProperties") MongoProperties properties, MongoConverter converter) {
        MongoConnectionDetails connectionDetails = new PropertiesMongoConnectionDetails(properties,null);
        MongoClient mongoClient = MongoClients.create(connectionDetails.getConnectionString());
        SimpleMongoClientDatabaseFactory mongoClientDatabaseFactory = new SimpleMongoClientDatabaseFactory(mongoClient, properties.getDatabase());
        return new MongoTemplate(mongoClientDatabaseFactory, converter);
    }

    @Bean(name = "datasetMongoProperties")
    @ConfigurationProperties(prefix = "dataset.mongodb")
    public MongoProperties datesetMongoProperties() {
        log.info("-------------------- datasetMongoProperties init ---------------------");
        return new MongoProperties();
    }
}