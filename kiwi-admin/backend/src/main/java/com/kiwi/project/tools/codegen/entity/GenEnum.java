package com.kiwi.project.tools.codegen.entity;

import org.springframework.boot.jdbc.DatabaseDriver;

public interface GenEnum
{
    public static enum GenTpl
    {
        CRUD("crud", "单表（增删改查）"),
        TREE("tree", "树表（增删改查）"),
        SUB("sub", "主子表（增删改查）");

        private final String code;
        private final String info;

        GenTpl(String code, String info) {
            this.code = code;
            this.info = info;
        }

        public String getCode() {
            return code;
        }

        public String getInfo() {
            return info;
        }
    }


    public enum WebTpl
    {
        Angular;
    }

    public enum DaoTpl
    {
        MybatisPlus, MongoDB;
    }

    public enum DatabaseType
    {
        MySQL(true),
        Oracle(true),
        SQLServer(true),
        PostgreSQL(true),
        MongoDB(false),
        ;

        private final boolean sql;

        DatabaseType(boolean sql) {
            this.sql = sql;
        }

        public boolean isSql() {
            return sql;
        }

        public static DatabaseType fromUrl(String url) {
            DatabaseDriver databaseDriver = DatabaseDriver.fromJdbcUrl(url);
            switch( databaseDriver ) {
                case MYSQL:
                    return MySQL;
                case ORACLE:
                    return Oracle;
                case SQLSERVER:
                    return SQLServer;
                case POSTGRESQL:
                    return PostgreSQL;
                default:
                    return null;
            }
        }
    }
}
