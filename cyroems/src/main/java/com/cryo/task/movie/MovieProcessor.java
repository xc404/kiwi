package com.cryo.task.movie;

import com.cryo.common.mongo.MongoTemplate;
import com.cryo.task.engine.Handlers;
import com.cryo.task.engine.InstanceProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MovieProcessor extends InstanceProcessor
{
    @Value("${app.task.movie.maxPoolSize:100}")
    private int maxPoolSize = 100;

    public MovieProcessor(Handlers movieDispatcher, MongoTemplate mongoTemplate) {
        super(movieDispatcher, mongoTemplate);
    }

    @Override
    public int getPoolSize() {
        return this.maxPoolSize;
    }
}
