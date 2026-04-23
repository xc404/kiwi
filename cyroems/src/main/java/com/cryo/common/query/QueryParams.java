package com.cryo.common.query;

import com.cryo.common.utils.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.ReflectionUtils;

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

    public static QueryParams from(Object obj) {
        List<QueryParam> params = new ArrayList<>();
        ReflectionUtils.doWithFields(obj.getClass(), f -> {
            if( !f.canAccess(obj) ) {
                return;
            }
            String name = f.getName();
            Object value = f.get(obj);
            if( value == null ) {
                return;
            }
            QueryParam.QueryFieldOP op = ArrayUtils.isArrayOrCollection(value) ? QueryParam.QueryFieldOP.IN : QueryParam.QueryFieldOP.EQ;
            QueryField queryField = AnnotationUtils.getAnnotation(f, QueryField.class);
            if( queryField != null ) {
                String name1 = queryField.value();
                if( StringUtils.isNotBlank(name1) ) {
                    name = name1;
                }
                op = queryField.op();
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
            switch( param.op() ) {
                case EQ:
                    criteria.is(param.value());
                    break;
                case GT:
                    criteria.gt(param.value());
                    break;
                case GTE:
                    criteria.gte(param.value());
                    break;
                case LT:
                    criteria.lt(param.value());
                    break;
                case LTE:
                    criteria.lte(param.value());
                    break;
                case LIKE:
                    criteria.regex((String) param.value(), "i");
                    break;
                case IN:
                    Object[] objectArray = ArrayUtils.toObjectArray(param.value());
                    if( objectArray.length
                            != 0 ) {
                        criteria.in((Object[]) objectArray);
                    } else {
                        criteriaMap.remove(param.name());
                    }
                    break;
            }
        });
        Query query = new Query();
        criteriaMap.forEach((k, v) -> {
            if( !v.getCriteriaObject().isEmpty() ) {
                query.addCriteria(v);
            }
        });
        return query;
    }
}
