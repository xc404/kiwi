package com.kiwi.project.system.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Dict
{
    private String code;
    private String name;
    private String groupCode;
    private String description;
}
