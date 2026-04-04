package com.kiwi.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.UtilityClass;
import lombok.experimental.Delegate;

/**
 * 全项目共享的 Jackson {@link ObjectMapper} 门面：通过静态方法委托 {@link ObjectMapper} 的全部公开实例方法。
 */
@UtilityClass
public class JsonUtils {

    @Delegate
    private final ObjectMapper JSON = new ObjectMapper();
}
