package com.cryo.task.tilt;

import com.cryo.common.mongo.MongoTemplate;
import com.cryo.task.engine.Handlers;
import com.cryo.task.engine.InstanceProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MDocProcessor extends InstanceProcessor
{
    @Value("${app.task.mdoc.maxPoolSize:30}")
    private int maxPoolSize = 30;

    public MDocProcessor(Handlers movieDispatcher, MongoTemplate mongoTemplate) {
        super(movieDispatcher, mongoTemplate);
    }

    @Override
    public int getPoolSize() {
        return this.maxPoolSize;
    }
}
