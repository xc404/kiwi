package com.kiwi.framework.web.ctl;

import com.kiwi.framework.session.SessionService;
import com.kiwi.framework.session.SessionUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * web层通用数据处理
 * 
 * @author ruoyi
 */
@RequiredArgsConstructor
@Slf4j
public class BaseController
{

    protected final SessionService sessionService;

    /**
     * 获取登录用户id
     */
    public String getUserId()
    {
        return getSessionUser().getId();
    }

    /**
     * 获取登录部门id
     */
    public String getDeptId()
    {
        return getSessionUser().getDeptId();
    }

    /**
     * 获取登录用户名
     */
    public String getUsername()
    {
        return getSessionUser().getUserName();
    }


    /**
     * 获取用户缓存信息
     */
    public SessionUser getSessionUser()
    {
        return sessionService.getCurrentUser();
    }

}
