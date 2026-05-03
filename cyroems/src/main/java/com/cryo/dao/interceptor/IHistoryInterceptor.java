package com.cryo.dao.interceptor;

import com.cryo.model.IHistoryEntity;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

public class IHistoryInterceptor extends AbstractMongoEventListener<IHistoryEntity> {


    @Override
    public void onAfterConvert(AfterConvertEvent<IHistoryEntity> event) {
        super.onAfterConvert(event);
    }

    @Override
    public void onBeforeSave(BeforeSaveEvent<IHistoryEntity> event) {
        super.onBeforeSave(event);
    }
}
