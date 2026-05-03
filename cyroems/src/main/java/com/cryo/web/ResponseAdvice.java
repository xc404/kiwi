package com.cryo.web;

import cn.hutool.core.util.ArrayUtil;
import com.cryo.common.model.ContentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dreamlu.mica.core.result.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Collection;
import java.util.List;

/**
 * 拦截controller返回值，封装后统一返回格式
 */
@RestControllerAdvice(basePackages = "com.cryo")
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private ObjectMapper objectMapper;


    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object o, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if(o instanceof byte[]){
            return o;
        }
        if (o instanceof String) {
            try {
                return objectMapper.writeValueAsString(R.success(o));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        if(o == null){
            return R.success();
        }
        //如果返回的结果是R对象，即已经封装好的，直接返回即可。
        //如果不进行这个判断，后面进行全局异常处理时会出现错误
        if (o instanceof R) {
            return o;
        }
        if(o instanceof Collection ){
            return R.success(new ContentResult<>((Collection<?>) o));
        }
        if(o.getClass().isArray()){
            return R.success(new ContentResult<>(List.of(ArrayUtil.wrap(o))));
        }
        return R.success(o);
    }
}
