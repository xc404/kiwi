package com.cryo.common.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

/**
 * Created by chaox on 5/8/2017.
 */
@WritingConverter
@Component
public class JsonWriteConverter implements Converter<JsonNode, Document>
{
    @Override
    public Document convert(JsonNode source) {
        return Document.parse(source.toString());
    }
}
