package com.kiwi.framework.error;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import com.kiwi.project.ai.AiModelErrorUtils;
import net.dreamlu.mica.core.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ResponseBody;


@ConditionalOnWebApplication
@ControllerAdvice
public class ExceptionHandler
{

    private final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    @ResponseBody
    @org.springframework.web.bind.annotation.ExceptionHandler(NonTransientAiException.class)
    public R<?> handleNonTransientAi(NonTransientAiException e) {
        logger.warn("大模型调用失败: {}", e.getMessage());
        return R.fail(AiModelErrorUtils.toUserMessage(e.getMessage()));
    }

    @ResponseBody
    @org.springframework.web.bind.annotation.ExceptionHandler
    public R<?> processException(Exception e) {
        logger.error(e.getMessage(), e);
        if (e instanceof NotLoginException) {
            return R.fail(SystemCode.TokenError, e.getMessage());
        }
        if (e instanceof SaTokenException) {
            return R.fail(SystemCode.NotPermission, e.getMessage());
        }
        return R.fail(e.getMessage());
    }

}
