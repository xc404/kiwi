package com.kiwi.framework.security;

import cn.dev33.satoken.stp.StpInterface;
import com.kiwi.framework.session.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface
{

    private final SessionService sessionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return this.sessionService.getPermissions();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return this.sessionService.getCurrentUser().getRoleIds();
    }

}