package com.cryo.springboot.mongo;

import com.cryo.common.mongo.BaseRepositoryImpl;
import com.cryo.common.mongo.JsonReadConverter;
import com.cryo.common.mongo.JsonWriteConverter;
import com.cryo.common.mongo.MongoTemplate;
import com.cryo.convert.MovieStepConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.util.TypeInformation;

import java.util.ArrayList;
import java.util.List;

@EnableMongoRepositories(basePackages = "com.cryo.dao",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com.cryo.dao.dataset.*"),
        repositoryBaseClass = BaseRepositoryImpl.class, mongoTemplateRef = "mongoTemplate")
@Configuration()
@Slf4j
public class MongoModule
{


    @Primary
    @Bean("mongoTemplate")
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory, MongoConverter converter) {
        return new MongoTemplate(mongoDatabaseFactory, converter);
    }

    @Primary
    @Bean(name = "primaryMongoProperties")
    @ConfigurationProperties(prefix = "spring.data.mongodb")
    public MongoProperties primaryMongoProperties() {
        log.info("-------------------- primaryMongoProperties init ---------------------");
        return new MongoProperties();
    }

    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converterList = new ArrayList<>();
        converterList.add(new MovieStepConverter());
        converterList.add(new JsonReadConverter());
        converterList.add(new JsonWriteConverter());
        return new MongoCustomConversions(converterList);
    }

    @Bean
    MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory, MongoMappingContext context,
                                                MongoCustomConversions conversions) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter mappingConverter = new MappingMongoConverter(dbRefResolver, context)
        {
            @Override
            protected Object getPotentiallyConvertedSimpleRead(Object value, TypeInformation<?> target) {
                try {
                    return super.getPotentiallyConvertedSimpleRead(value, target);
                } catch( IllegalArgumentException e ) {
                    if( Enum.class.isAssignableFrom(target.getType()) ) {
                        log.warn("{} convert error: {}", target.getType(), value);
                        return null;
                    }
                    throw e;
                }
            }
        };
        mappingConverter.setCustomConversions(conversions);
        // Map keys derived from paths/names may contain '.' (e.g. -41.00 in positions); BSON field names cannot use raw dots.
        mappingConverter.setMapKeyDotReplacement("__DOT__");
        return mappingConverter;
    }

}