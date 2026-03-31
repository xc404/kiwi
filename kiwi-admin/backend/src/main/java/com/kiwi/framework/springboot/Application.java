package com.kiwi.framework.springboot;

import org.camunda.bpm.spring.boot.starter.CamundaBpmAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
/**
 * Created by chaox on 11/24/2017.
 */
@SpringBootApplication(scanBasePackages = "com.kiwi")
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@EnableScheduling()
@EnableMongoAuditing(modifyOnCreate = true)
@Import({CamundaBpmAutoConfiguration.class})
@EnableTransactionManagement
@EnableAsync
public class Application extends SpringBootServletInitializer
{


    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Application.class);
        app.run();
        try {
            Thread.currentThread().join();
        } catch( InterruptedException e ) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }

}
