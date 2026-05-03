package com.cryo.common.model;

import lombok.Data;
import org.springframework.data.annotation.Id;

@Data
public class IdEntity {
    @Id
    protected String id;
}
