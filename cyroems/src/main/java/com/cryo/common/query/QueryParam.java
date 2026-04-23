package com.cryo.common.query;


public record QueryParam(String name, QueryFieldOP op, Object value) {


    public enum QueryFieldOP {
        EQ,//等于
        LIKE,//模糊查询
        IN,// in
        GT,// 大于
        GTE,//大于等于
        LT,//小于
        LTE,    //小于等于
    }
}
