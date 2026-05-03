package com.kiwi.project.notification.ctl;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.kiwi.framework.ctl.BaseCtl;
import com.kiwi.project.notification.NotificationService;
import com.kiwi.project.notification.dto.NotificationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@Tag(name = "站内消息")
@SaCheckLogin
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationCtl extends BaseCtl {

    private final NotificationService notificationService;

    @Operation(operationId = "notif_list", summary = "当前登录用户的站内消息列表（按创建时间倒序）")
    @GetMapping
    public List<NotificationDto> list() {
        String uid = getCurrentUserId();
        if (uid == null) {
            return Collections.emptyList();
        }
        return notificationService.listForUser(uid);
    }
}
