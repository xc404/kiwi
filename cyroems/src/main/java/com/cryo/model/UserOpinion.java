package com.cryo.model;

import com.cryo.common.model.DataEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserOpinion extends DataEntity
{
    private String email;
    private String userId;
    private String content;
}
