package com.kiwi.project.tools.jdbc.connection.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
     */
    private String password;

    /**
     * The JDBC URL for the connection.
     */
    private String jdbcUrl;

    @JsonIgnore
    public String getPassword() {
        return password;
    }
}