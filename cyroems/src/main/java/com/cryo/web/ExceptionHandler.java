package com.cryo.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import com.cryo.common.model.SystemCode;
import net.dreamlu.mica.core.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseBody;


@ConditionalOnWebApplication
@ControllerAdvice
public class ExceptionHandler
{

    private final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    @ResponseBody
    @org.springframework.web.bind.annotation.ExceptionHandler
    public R processException(Exception e) {
        Throwable unwrap = e;
        logger.error(e.getMessage(), e);
        if(e instanceof NotLoginException ){
            return R.fail(SystemCode.TokenError,e.getMessage());
        }
        if(e instanceof SaTokenException){
            return R.fail(SystemCode.NotPermission,e.getMessage());
        }
        return R.fail(unwrap.getMessage());
    }

}
