package com.kiwi.common.query;

import com.kiwi.common.utils.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.ReflectionUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryParams
{
    private final List<QueryParam> params;

    public QueryParams(List<QueryParam> params) {
        this.params = params;
    }

    public static QueryParams of(Map<String, Object> params) {
        List<QueryParam> queryParams = new ArrayList<>();
        params.forEach((key, value) -> {
            if( StringUtils.isBlank(key) ) {
                return;
            }
            if( value == null ) {
                return;
            }
            String[] keyName = key.split("\\|");
            String name = keyName[0];
            QueryField.Type op = QueryField.Type.EQ;
            if( keyName.length == 2 ) {
                op = QueryField.Type.valueOf(keyName[1]);
            }
            if( ArrayUtils.isArrayOrCollection(value) ) {
                op = QueryField.Type.IN;
            }
            queryParams.add(new QueryParam(name, op, value));
        });
        return new QueryParams(queryParams);
    }

    public static QueryParams of(Object obj) {
        List<QueryParam> params = new ArrayList<>();


        //todo support nested fields
        ReflectionUtils.doWithFields(obj.getClass(), f -> {

            PropertyDescriptor propertyDescriptor = BeanUtils.getPropertyDescriptor(obj.getClass(), f.getName());
            Method readMethod = propertyDescriptor != null ? propertyDescriptor.getReadMethod() : null;
            Object value = null;
            QueryField queryField = AnnotationUtils.getAnnotation(f, QueryField.class);
            if( readMethod != null ) {
                queryField = AnnotationUtils.getAnnotation(readMethod, QueryField.class);
                try {
                    value = readMethod.invoke(obj);
                } catch( InvocationTargetException e ) {
                    throw new RuntimeException(e);
                }
            } else if( f.canAccess(obj) ) {
                value = f.get(obj);
            }
            if( value == null ) {
                return;
            }
            if( queryField != null ) {
                queryField = AnnotationUtils.getAnnotation(f, QueryField.class);
            }

            String name = f.getName();
            QueryField.Type op = ArrayUtils.isArrayOrCollection(value) ? QueryField.Type.IN : QueryField.Type.EQ;
            if( queryField != null ) {
                String name1 = queryField.value();
                if( StringUtils.isNotBlank(name1) ) {
                    name = name1;
                }
                op = queryField.condition();
            } else {
                Field annotation = AnnotationUtils.getAnnotation(f, Field.class);
                if( annotation != null ) {
                    String name1 = annotation.name();
                    if( StringUtils.isNotBlank(name1) ) {
                        name = name1;
                    }
                }
            }

            params.add(new QueryParam(name, op, value));
        });
        return new QueryParams(params);
    }

    public Query toMongo() {
        Map<String, Criteria> criteriaMap = new HashMap<>();
        params.forEach(param -> {
            Criteria criteria = criteriaMap.computeIfAbsent(param.name(), k -> Criteria.where(param.name()));
            switch( param.type() ) {
                case EQ:
                    criteria.is(param.value());
                    break;
                case GTE:
                    criteria.gte(param.value());
                    break;
                case LTE:
                    criteria.lte(param.value());
                    break;
                case LIKE:
                    criteria.regex((String) param.value(), "i");
                    break;
                case IN:
                    criteria.in(ArrayUtils.toObjectArray(param.value()));
                    break;
                case BETWEEN:
                    Object[] objectArray = ArrayUtils.toObjectArray(param.value());
                    if( objectArray.length == 2 ) {
                        if( objectArray[0] != null ) {
                            criteria.gte(objectArray[0]);
                        }
                        if( objectArray[1] != null ) {
                            criteria.lte(objectArray[1]);
                        }
                    }
                    if( objectArray.length == 1 ) {
                        criteria.gte(objectArray[0]);
                    }
                    break;
            }
        });
        Query query = new Query();
        criteriaMap.forEach((k, v) -> query.addCriteria(v));
        return query;
    }
}
