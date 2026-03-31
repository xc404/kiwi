package com.kiwi.project.tools.codegen.utils;

import cn.hutool.core.util.StrUtil;
import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.GenEnum;
import com.kiwi.project.tools.codegen.entity.GenField;
import com.kiwi.project.tools.codegen.entity.JdbcType;
import com.kiwi.project.tools.codegen.entity.vo.CodeGenVo;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static cn.hutool.core.text.NamingCase.toCamelCase;

public class JdbcTableReader
{


    public static final String TABLE_CAT = "TABLE_CAT";
    public static final String TABLE_SCHEM = "TABLE_SCHEM";
    public static final String TABLE_NAME = "TABLE_NAME";
    public static final String TABLE_TYPE = "TABLE_TYPE";
    public static final String REMARKS = "REMARKS";
    public static final String PKTABLE_CAT = "PKTABLE_CAT";
    public static final String PKTABLE_SCHEM = "PKTABLE_SCHEM";
    public static final String PKTABLE_NAME = "PKTABLE_NAME";
    public static final String COLUMN_NAME = "COLUMN_NAME";
    public static final String DATA_TYPE = "DATA_TYPE";
    public static final String TYPE_NAME = "TYPE_NAME";
    public static final String COLUMN_SIZE = "COLUMN_SIZE";
    public static final String DECIMAL_DIGITS = "DECIMAL_DIGITS";
    public static final String IS_NULLABLE = "IS_NULLABLE";
    public static final String TYPE = "TYPE";
    public static final String INDEX_NAME = "INDEX_NAME";
    public static final String FK_NAME = "FK_NAME";
    public static final String PK_NAME = "PK_NAME";
    public static final String KEY_SEQ = "KEY_SEQ";
    public static final String PKCOLUMN_NAME = "PKCOLUMN_NAME";
    public static final String FKCOLUMN_NAME = "FKCOLUMN_NAME";

//    public static CodeGenVo readTable(ConnectionSettings connectionDetails, String tableName) {
//        DataSource dataSource = createDataSource(connectionDetails);
//        try( var con = dataSource.getConnection() ) {
//
//            return readTable(con, tableName);
//        } catch( SQLException e ) {
//            throw new RuntimeException(e);
//        }
//
//    }

    public static CodeGenVo readTable(Connection con, String tableName) throws SQLException {

        DatabaseMetaData metaData = con.getMetaData();
        GenEnum.DatabaseType databaseType = GenEnum.DatabaseType.fromUrl(con.getMetaData().getURL());
        try( ResultSet tables = metaData.getTables(con.getCatalog(), con.getSchema(), tableName, new String[]{"TABLE"}) ) {
            while( tables.next() ) {
                GenEntity genEntity = new GenEntity();
                String tblName = tables.getString(TABLE_NAME);
                String tblCat = tables.getString(TABLE_CAT);
                String tblSchema = tables.getString(TABLE_SCHEM);
                // 修复 bug：原处误用了外部参数 tableName
                genEntity.setTableName(tblName);
                genEntity.setTableComment(tables.getString(REMARKS));
                genEntity.setClassName(StrUtil.upperFirst(toCamelCase(tblName)));
                genEntity.setDatabaseType(databaseType);

                genEntity.setTableCatalog(tblCat);
                genEntity.setTableSchema(tblSchema);

                List<GenField> genFields = new ArrayList<>();
                try( ResultSet column = metaData.getColumns(con.getCatalog(), con.getSchema(), tblName, null) ) {
                    while( column.next() ) {
                        GenField genField = new GenField();
                        String columnName = column.getString("COLUMN_NAME");
                        int dataType = column.getInt("DATA_TYPE");

                        int columnSize = column.getInt("COLUMN_SIZE");
                        String remarks = column.getString("REMARKS");

                        genField.setColumnName(columnName);
                        genField.setColumnType(JdbcType.forCode(dataType));
                        genField.setLength(columnSize);
                        genField.setColumnComment(remarks);
                        // genColumn.setPrecision(column.getInt("...")); // 待确认字段名称后再启用

                        genFields.add(genField);
                    }
                }

                return new CodeGenVo(genEntity, genFields);
            }
        } catch( SQLException e ) {
            throw new SQLException("Error reading table metadata for: " + tableName, e);
        }
        return null;
    }


//    private static DataSource createDataSource(ConnectionSettings connectionDetails) {
//        String driverClassName = DatabaseDriver.fromJdbcUrl(connectionDetails.getJdbcUrl()).getDriverClassName();
//        DataSourceBuilder<? extends DataSource> builder = DataSourceBuilder.create();
//        builder.driverClassName(driverClassName);
//        DataSource dataSource = builder.url(connectionDetails.getJdbcUrl())
//                .username(connectionDetails.getUsername())
//                .password(connectionDetails.getPassword())
//                .build();
//        return dataSource;
//    }

    public static List<CodeGenVo> readTables(Connection con, List<String> tables) {
        List<CodeGenVo> result = new ArrayList<>();
        for( String tableName : tables ) {
            try {
                CodeGenVo codeGenVo = readTable(con, tableName);
                if( codeGenVo != null ) {
                    result.add(codeGenVo);
                }
            } catch( Exception e ) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }
}
