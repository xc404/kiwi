package com.kiwi.project.system.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DictGroup
{
    private String code;
    private String name;
    private List<Dict> dict;
}
