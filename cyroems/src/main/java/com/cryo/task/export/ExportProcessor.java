package com.cryo.task.export;

import com.cryo.common.mongo.MongoTemplate;
import com.cryo.model.Instance;
import com.cryo.task.engine.Handlers;
import com.cryo.task.engine.InstanceProcessor;
import com.cryo.task.movie.MovieProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExportProcessor extends InstanceProcessor
{
    @Value("${app.task.export.maxPoolSize:100}")
    private int maxPoolSize = 100;


    public ExportProcessor(Handlers movieDispatcher, MongoTemplate mongoTemplate) {
        super(movieDispatcher, mongoTemplate);
    }

    @Override
    public int getPoolSize() {
        return this.maxPoolSize;
    }
}
