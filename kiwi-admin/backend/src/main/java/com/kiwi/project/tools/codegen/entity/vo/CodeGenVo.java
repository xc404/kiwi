package com.kiwi.project.tools.codegen.entity.vo;

import com.kiwi.project.tools.codegen.entity.GenEntity;
import com.kiwi.project.tools.codegen.entity.GenField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CodeGenVo
{
    private GenEntity genEntity;
    private List<GenField> fields;
}
