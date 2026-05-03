package com.cryo.common.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import net.dreamlu.mica.core.utils.JsonUtil;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

/**
 * Created by chaox on 5/8/2017.
 */
@ReadingConverter
@Component
public class JsonReadConverter implements Converter<Document, JsonNode>
{
    @Override
    public JsonNode convert(Document source) {
        return JsonUtil.readTree(source.toJson());
    }
}
