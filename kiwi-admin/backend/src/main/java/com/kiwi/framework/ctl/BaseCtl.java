package com.kiwi.framework.ctl;


import com.kiwi.framework.session.SessionService;
import com.kiwi.framework.session.SessionUser;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseCtl
{
    private SessionService sessionService;



    public SessionService getSessionService() {
        return sessionService;
    }
    @Autowired
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }


    public SessionUser getCurrentUser() {
        return sessionService.getCurrentUser();
    }

    public String getCurrentUserId() {
        SessionUser user = getCurrentUser();
        return user != null ? user.getId() : null;
    }
}
