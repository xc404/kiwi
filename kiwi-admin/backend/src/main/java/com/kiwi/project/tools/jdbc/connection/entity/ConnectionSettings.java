package com.kiwi.project.tools.jdbc.connection.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kiwi.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents the settings required to establish a JDBC connection.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Document("toolConnectionSettings")
public class ConnectionSettings extends BaseEntity<String>
{
    /**
     * The name of the connection.
     */
    private String name;

    /**
     * The username used for the JDBC connection.
     */
    private String username;

    /**
     * The password used for the JDBC connection.
     * 请求体可写入；响应不输出明文（列表/详情/新增返回均不含 password）。
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * The JDBC URL for the connection.
     */
    private String jdbcUrl;
}