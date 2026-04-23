package com.cryo.springboot;

import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.stereotype.Component;

@Component
public class PageableArgumentResolverCustomizer implements PageableHandlerMethodArgumentResolverCustomizer
{
    @Override
    public void customize(PageableHandlerMethodArgumentResolver pageableResolver) {
        pageableResolver.setMaxPageSize(Short.MAX_VALUE * 2);
    }
}
